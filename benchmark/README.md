# k6 재측정 가이드 (Order-Me-Plz)

부하 생성기 = **데스크탑(유선)**, 서버+MySQL = **노트북(유선 연결)** 기준.
k6는 데스크탑에만 설치한다.

```bash
winget install k6 --source winget   # 또는 choco install k6
k6 version
```

---

## 1. 무엇을 무엇과 비교하는가

| 측정 | 브랜치 | 스크립트 | 비고 |
|---|---|---|---|
| 바닥값 (네트워크+프레임워크) | main | `k6/00-network-floor.js` | `/ping`, 딱 1회 |
| 주문 · 개선 전 (동기) | **main** | `k6/01-order-api.js -e MODE=sync` | 같은 빌드에 두 엔드포인트가 공존 |
| 주문 · 개선 후 (비동기) | **main** | `k6/01-order-api.js` (MODE=async) | |
| 리뷰 · 개선 전 (단일 트랜잭션) | `bench/review-before` | `k6/02-review-api.js` | 데드락 재현이 목적 |
| 리뷰 · 개선 후 (통계 분리+이벤트) | main | `k6/02-review-api.js` | 정합성 0건 불일치가 목적 |

- 주문 전/후는 **checkout 없이** 같은 서버에서 URL만 바꿔 측정한다 (빌드·스키마·JVM 동일 → 단일 변수 비교).
- 옛 nGrinder 수치와는 도구가 다르므로 비교하지 않는다. 전/후 모두 k6로 새로 잰다.

## 2. 측정 유형 2가지 — 목적에 맞게 사용

1. **고정 rate (constant-arrival-rate)**: "초당 N건 유입 시 p95/p99와 에러율" — 지연 비교용.
   전/후 **모두 감당 가능한 rate**로 고정해야 비교가 성립한다.
2. **용량 램프 (ramping-arrival-rate)**: rate를 계단식으로 올려 **p95가 목표(200ms)를 뚫는 지점의 rate = 용량**.
   "처리량 개선" 주장의 근거는 이 방식으로 만든다 (01 스크립트 주석의 시나리오로 교체).

## 3. 실행 순서

### 사전 준비 (1회)
1. 노트북: 전원 연결 + "최고 성능" 전원 계획, 방화벽 8080 인바운드 허용, `ipconfig`로 IP 확인.
2. 서버 빌드·기동 (힙 고정 — 리사이즈 노이즈 제거):
   ```bash
   ./gradlew bootJar
   java -Xms2g -Xmx2g -jar build/libs/OrderMePlz-0.0.1-SNAPSHOT.jar
   ```
3. 새 스키마로 1회 기동해 테이블 생성 확인 후 `sql/seed.sql` 실행.
4. 스모크: `k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=10 -e DURATION=30s k6/01-order-api.js`
   → check 실패 0 확인. 02도 동일하게 스모크.

### 매 회차 공통 절차
1. `sql/reset-round.sql` 실행 (데이터 조건 동일화)
2. 서버 재시작 → **워밍업**: 본측정과 같은 스크립트를 RATE 낮춰 1~2분 실행하고 결과는 버린다 (JIT)
3. `sql/verify.sql`의 데드락 카운터를 실행해 **시작 전 값 기록**
4. 본측정 실행 (아래 명령)
5. 종료 후: verify.sql 실행(리뷰는 1~2분 뒤), 로그 카운트, 결과 JSON을 `benchmark/results/`에 보관
6. **같은 조건으로 3회 반복** → 중앙값 사용. 회차 사이 5분 휴식(노트북 온도).

### 본측정 명령
```bash
# 주문 (async / sync)
k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=1000 -e DURATION=15m k6/01-order-api.js
k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=1000 -e DURATION=15m -e MODE=sync k6/01-order-api.js

# 리뷰 (개선 후 = main, 개선 전 = bench/review-before 로 checkout 후 재빌드·재기동)
k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=20 -e DURATION=15m -e SHOP_POOL=10 k6/02-review-api.js
```

### 리뷰 "개선 전" 회차 주의
- `git checkout bench/review-before` → bootJar 재빌드 → 기동.
  첫 기동 시 ddl-auto=update가 shops에 구 설계 컬럼(average_rating 등)을 추가한다.
- 기동 후 `reset-round.sql` 하단의 주석 처리된 `UPDATE shops SET average_rating=0, ...`을 실행해야
  평점 갱신 시 NPE가 나지 않는다.
- 이 회차는 데드락으로 500이 섞이는 게 **정상이며 그것이 증거**다. `http_req_failed` 비율과
  lock_deadlocks 증가분을 기록한다.

## 4. 서버 측 지표 수집 (k6가 못 재는 것)

비동기 주문 측정 중 데스크탑에서 큐 잔여량 폴링 (5초 간격):

```bash
# bash (Git Bash)
while true; do echo "$(date +%T) $(curl -s 'http://<서버IP>:8080/actuator/metrics/executor.queued?tag=name:insertTaskExecutor' | grep -o '"value":[0-9.]*')"; sleep 5; done | tee executor-queue.log
```
```powershell
# PowerShell
while ($true) { $v = (Invoke-RestMethod "http://<서버IP>:8080/actuator/metrics/executor.queued?tag=name:insertTaskExecutor").measurements[0].value; "$(Get-Date -Format HH:mm:ss) queued=$v" | Tee-Object -FilePath executor-queue.log -Append; Start-Sleep 5 }
```

- 테스트 종료 시각부터 queued=0이 될 때까지의 시간 = **큐 소진 시간** (End-to-End 근거).
- 저장 완료량 / 정합성 / 데드락은 `sql/verify.sql`.
- 실패 건수: 서버 로그에서 `order create fail`, `review stats update fail` 카운트.

## 5. 결과 읽는 법

| 지표 | 의미 | 기록할 것 |
|---|---|---|
| `http_req_duration` | 요청-응답 시간 | avg, p90, **p95, p99**, max |
| `http_req_failed` | 실패율 | rate |
| `iterations` | 총 요청 수 | count → "15분 접수량" |
| `dropped_iterations` | k6가 목표 rate를 못 맞춰 버린 요청 | **0이어야** "초당 N건 유입 유지" 주장 성립. 쌓이면 maxVUs 부족 또는 서버 포화 |
| `checks` | 검증 통과율 | 100%인지 |

해석 노트:
- 바닥값 p95가 5ms인데 API p95가 180ms → 차이는 서버 처리 시간.
- 비동기 주문에서 503이 나오기 시작하면 insertTaskExecutor 포화 → 접수 거절(백프레셔). 503이 0인 최대 rate가 이 구조의 접수 용량이다.
  k6 `orders_rejected`(503 수)와 서버 `/actuator/metrics/omp.order.async.rejected`가 일치해야 한다.
  (CallerRunsPolicy는 제거됨. 포화 시 톰캣 스레드가 몰래 INSERT하는 구간은 이제 없다.)
- 유실 검증: k6 `orders_accepted`(202 수) == 종료 후 `SELECT COUNT(*) FROM orders`.
  리뷰는 `omp.review.stats.rejected`, `omp.review.stats.failed`가 0이어야 verify.sql 0행이 의미를 가진다.
- `iterations`(시도) vs `orders_accepted`(접수) vs `completed_orders`(저장 완료) vs 큐 소진 시간 → 접수 성능과 실제 완료를 분리해 보고.

## 6. 매 측정 기록 환경 표 (결과 문서에 복사)

```
| 항목 | 값 |
|---|---|
| 측정일시 / 회차 | 2026-XX-XX / N회차 (3회 중) |
| 서버 | 노트북 모델, CPU, RAM, 전원 연결+최고 성능 모드 |
| 네트워크 | 서버 유선/무선, 부하기 유선 |
| JVM | 버전, -Xms/-Xmx |
| MySQL | 버전, innodb_buffer_pool_size, 앱과 동거 |
| 커넥션 풀 | HikariCP 20 (단일 풀) |
| 스레드 풀 | `omp.executor.*` 값 (기본 insert 10/30/q100, reviewStats 10/20/q2000), 거절 정책 Abort |
| 리뷰 통계 모드 | `omp.review.stats.mode` = sync / async |
| 거절·실패 카운터 | omp.order.async.rejected, omp.review.stats.rejected, omp.review.stats.failed (시작 전/종료 후) |
| 데이터 | seed.sql (users 10만, shops 1천, carts 10만), 회차마다 reset-round.sql |
| 부하 | executor, rate, duration, maxVUs |
| 바닥값 | /ping p95 = X ms |
| 데드락 카운터 | 시작 N → 종료 M (차이 = 발생 건수) |
```

## 7. 흔한 함정

- **부하기 모니터링**: k6 실행 중 데스크탑 CPU 90% 초과 시 부하기 병목 → 결과 무효.
- **노트북 온도**: HWiNFO 등으로 클럭 기록, 스로틀링 회차는 표시.
- **IP 변동**: WiFi↔유선 전환 시 IP 바뀜. 회차마다 확인.
- **actuator 노출**: 현재 `management.endpoints.web.exposure.include=*` — 벤치마크 편의용이므로 외부 배포 시 축소.
- **테이블명 대소문자**: Windows MySQL은 대소문자 무시. 서버를 Linux로 옮기면 소문자 테이블명 기준으로 SQL 확인.
