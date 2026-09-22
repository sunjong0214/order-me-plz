# k6 재측정 가이드 (Order-Me-Plz)

부하 생성기 = **데스크탑(유선)**, 서버+MySQL = **노트북(유선 연결)** 기준. k6는 데스크탑에만 설치한다.

```bash
winget install k6 --source winget   # 또는 choco install k6
k6 version                          # 환경 표에 기록
```

선행 문서: [REVIEW-2026-09-08.md](REVIEW-2026-09-08.md) (측정 설계 검토), [STEP1-2026-09-19.md](STEP1-2026-09-19.md) (측정 전 코드 수정).
2026-09-22에 두 문서의 지적(워밍업 순서, threshold, 판정 기준, 지표 정의, 리뷰 3단계)을 이 가이드와 스크립트·SQL에 반영했다.

---

## 0. 빌드·기동 (서버 노트북)

| 항목 | 값 |
|---|---|
| JDK | 21 (`java -version` 확인. 빌드 toolchain도 21) |
| 빌드 | `./gradlew bootJar`. `gradle/wrapper/`는 gitignore 대상이라 클론에 없으면 `gradle wrapper --gradle-version 8.14.3`으로 생성하거나 시스템 Gradle 사용 |
| DB 비밀번호 | `OMP_DB_PASSWORD` 환경변수 (미지정 시 1234) |
| 리뷰 통계 모드 | `omp.review.stats.mode` = `async`(기본) / `sync`. 기동 인자로 덮어쓴다 |
| 힙 | `-Xms2g -Xmx2g` 고정 (리사이즈 노이즈 제거) |

```bash
./gradlew bootJar
OMP_DB_PASSWORD=<비번> java -Xms2g -Xmx2g -jar build/libs/OrderMePlz-0.0.1-SNAPSHOT.jar
# 리뷰 ② 회차만: 위 명령 끝에  --omp.review.stats.mode=sync
```

## 1. 무엇을 무엇과 비교하는가

| 측정 | 브랜치 | 기동 옵션 | 스크립트·옵션 | 목적 |
|---|---|---|---|---|
| 바닥값 | main | | `00-network-floor.js` | `/ping`. 해석 참고선. 1회 |
| 주문 · 개선 전 (동기) | main | | `01-order-api.js -e MODE=sync` | 회차 A~C. 같은 빌드에 두 엔드포인트 공존 |
| 주문 · 개선 후 (비동기 접수) | main | | `01-order-api.js` (MODE=async) | 회차 A~D. 초안 [PORTFOLIO-1-2-draft.md](PORTFOLIO-1-2-draft.md) |
| 리뷰 ① shops 통계 + 같은 트랜잭션 | `bench/review-before` | | `02-review-api.js -e MODEL=closed -e TAG=before -e THRESHOLDS=off` | 데드락·유실 재현 |
| 리뷰 ② 별도 통계 + 같은 트랜잭션 | main | `--omp.review.stats.mode=sync` | `02-review-api.js -e MODEL=closed -e TAG=sync` | ①→② = 모델 분리 효과 |
| 리뷰 ③ 별도 통계 + AFTER_COMMIT 비동기 | main | (기본) | `02-review-api.js -e MODEL=closed -e TAG=async` | ②→③ = 비동기 효과 (지연 격리 vs 정합성 창) |

- 주문 전/후는 checkout 없이 같은 서버에서 URL만 바꾼다. 단, 동기 응답은 **저장 완료**까지, 비동기 응답은 **접수**까지라 계약이 다르다. 접수 지연과 커밋 완료량을 따로 기록하고 "저장 속도 개선"으로 쓰지 않는다.
- `bench/review-before`는 **main에서 분기**해 `Shop`의 통계 필드 3개와 `ReviewService.saveReviewBy`만 바꾼 재구성 브랜치다. 스레드 풀·검증·예외 처리·설정은 main과 같으므로 차이는 리뷰 갱신 설계 하나다. 옛 05ad2d8 기반 브랜치는 `bench/review-before-legacy-05ad2d8`로 남겨 두었고 측정에 쓰지 않는다 (CallerRuns 풀·Spring Retry·writerId 미할당이 섞여 비교가 오염된다).
- 옛 nGrinder 수치와는 도구가 다르므로 비교하지 않는다.

## 2. 측정 유형

1. **고정 rate** (`SCENARIO=fixed`, 기본): "초당 N건 유입 시 p95/p99와 에러율". 전/후 **모두 감당 가능한 rate**로 고정해야 비교가 성립한다.
2. **고정 동시 사용자** (`MODEL=closed`, 리뷰 ①②③): VUS명이 응답을 받는 즉시 다음 요청을 보낸다(nGrinder vUser 방식). 동시 요청 수가 항상 VUS로 고정되어 같은 가게 경합이 확실히 생기고, 서버가 빨라지면 15분 처리량이 늘어난다. 리뷰는 "같은 동시 사용자 수에서 처리량·p95·실패·정합성"을 비교하므로 이 방식을 쓴다. 주문은 유입량 목표가 있으므로 고정 rate(open)를 쓴다.
3. **용량**: "측정 조건에서 503(또는 p95>200ms) 없이 유지한 최대 유입률". k6 summary의 p95는 전체 집계라 램프 한 번으로는 한계 시점을 읽을 수 없다.
   - 탐색: `-e SCENARIO=ramp -e THRESHOLDS=off --out csv=...` (단계마다 RAMP 상승 + HOLD 유지). 시계열에서 HOLD 구간별 p95·503을 계산해 후보 구간을 본다.
   - 확정: 후보 rate마다 `SCENARIO=fixed`를 3~5분씩 따로 돌려 rate별 p95·503 표를 만든다 (계단식 고정 run). 15분 본측정은 확정 rate에서.

## 3. 실행 순서

### 사전 준비 (1회)
1. 노트북: 전원 연결 + "최고 성능" 전원 계획, 방화벽 8080 인바운드 허용, `ipconfig`로 IP 확인.
2. MySQL: `SET GLOBAL innodb_print_all_deadlocks = ON` (에러 로그에 데드락 전건 기록). `verify.sql` 1)의 `status`가 `enabled`인지 확인.
3. 새 스키마(OMP)로 main 서버 1회 기동 → 테이블 생성 확인 → `sql/seed.sql`.
4. 스모크: RATE 10, 30초로 01·02 실행 → check 실패 0.
5. 결과 폴더 `benchmark/results/`가 있는지 확인. k6는 폴더를 만들지 않으므로 `OUT_DIR`는 존재하는 경로여야 한다.

### 매 회차 공통 절차 (순서가 결과를 좌우한다)
1. 서버 재시작 (해당 브랜치·기동 옵션).
2. **워밍업**: 본측정과 같은 스크립트를 RATE 낮춰 1~2분 실행 (JIT). 결과는 버린다.
3. 워밍업 작업 **소진 확인**: 4절 폴링에서 `queued=0` **그리고** `active=0`.
4. `sql/reset-round.sql` 실행 (워밍업 쓰기 제거). ① 회차는 하단 `UPDATE shops ...`도 실행.
5. **시작 전 값 기록**: `verify.sql` 1)·1-b) (lock_deadlocks, row lock) + 카운터 3종 (`omp.order.async.rejected`, `omp.review.stats.rejected`, `omp.review.stats.failed`). 모두 기동 후 누적값이라 **증가분**으로 쓴다.
6. 4절 폴링 시작 → 본측정 실행.
7. 종료 후 **완료 대기**: `queued=0`·`active=0`이 되고 `COUNT(*)`가 더 변하지 않을 때까지. 제한 5분 초과 시 "미완료"로 기록한다.
8. `verify.sql` 전체 실행, 서버 로그 카운트(`order create fail`, `review stats update fail`), k6 JSON·CSV·폴링 CSV를 `benchmark/results/<날짜>-<TAG>-r<N>/`로 이동.
9. 같은 조건 **3회** → 성능은 중앙값과 범위, 오류·불일치는 **모든 회차의 건수**를 기록. 회차 사이 5분 휴식(노트북 온도).
10. 주문 회차 A(계단식)는 탐색용이라 1~5 를 rate 계단마다 반복하지 않는다. 시작 시 한 번 하고, 계단 사이에는 폴링으로 `queued=0`·`active=0` 만 확인한다. B·C·리뷰 ①②③은 1~9 전부.

### 본측정 명령 (데스크탑, 리포 루트에서)
```bash
S=http://<서버IP>:8080; OUT=benchmark/results

# ── 주문: 회차 A~D (PORTFOLIO-1-2-draft.md 검증 방법). 전부 open model. 모드는 MODE=sync / async, 같은 서버에서 URL만 다름.

# A. 접수 용량 — 계단식 고정 run. rate 마다 3~5분, sync·async 각각. THRESHOLDS=off (한계를 넘기는 게 목적. 판정은 결과값으로)
#    "p95 < 200ms, 실패 0, 503 0" 을 지키는 최대 rate = 모드별 접수 용량. A 는 워밍업·reset 을 rate 계단마다 반복하지 않고 한 번만 한다.
for R in 300 500 800 1000 1500; do
  k6 run -e BASE_URL=$S -e RATE=$R -e DURATION=4m -e MODE=sync  -e THRESHOLDS=off -e TAG=A-r$R -e OUT_DIR=$OUT k6/01-order-api.js
done
for R in 300 500 800 1000 1500; do
  k6 run -e BASE_URL=$S -e RATE=$R -e DURATION=4m -e MODE=async -e THRESHOLDS=off -e TAG=A-r$R -e OUT_DIR=$OUT k6/01-order-api.js
done
#    (탐색만 빠르게 하려면 -e SCENARIO=ramp --out csv=... 로 한 번 훑고, 후보 rate 만 위 고정 run 으로 확정)

# B. 같은 유입량 비교 — B_RATE = A 에서 확인한 sync 용량 근처. 15분 × 3회(TAG 의 r1~r3). THRESHOLDS=strict(기본): 둘 다 지켜야 비교 성립
B_RATE=<A에서 확정>
k6 run -e BASE_URL=$S -e RATE=$B_RATE -e DURATION=15m -e MODE=sync  -e TAG=B-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_sync_B-r1.csv  k6/01-order-api.js
k6 run -e BASE_URL=$S -e RATE=$B_RATE -e DURATION=15m -e MODE=async -e TAG=B-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_async_B-r1.csv k6/01-order-api.js

# C. 초과 유입 — C_RATE > sync 용량 (예: sync 용량의 1.5배). 15분. THRESHOLDS=off. MAX_VUS 를 넉넉히 (dropped 가 서버 포화인지 부하기 한계인지 구분).
#    sync: p95·max·dropped·5xx 로 붕괴 양상. async: orders_rejected(503) == omp.order.async.rejected 증가분, orders_accepted == completed_orders.
C_RATE=<A에서 확정>
k6 run -e BASE_URL=$S -e RATE=$C_RATE -e DURATION=15m -e MODE=sync  -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=C-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_sync_C-r1.csv  k6/01-order-api.js
k6 run -e BASE_URL=$S -e RATE=$C_RATE -e DURATION=15m -e MODE=async -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=C-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_async_C-r1.csv k6/01-order-api.js

# D. 완료 추적 — 별도 명령 없음. B·C 의 async 회차 동안 4절 폴링 CSV 를 켜 두고, k6 종료 후 queued=0·active=0 시각과 COUNT(*) 정지, hikari_pending 최대를 기록.

# 리뷰 ③ async(main 기본 기동) / ② sync(main, --omp.review.stats.mode=sync 기동) / ① before(bench/review-before)
#   closed model: VUS 명이 쉬지 않고 요청. 처리량 = reviews_created / 15m. VUS·SHOP_POOL 은 파일럿으로 확정한 값을 세 줄에 동일하게.
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=async  -e OUT_DIR=$OUT k6/02-review-api.js
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=sync   -e OUT_DIR=$OUT k6/02-review-api.js
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=before -e OUT_DIR=$OUT -e THRESHOLDS=off k6/02-review-api.js
```

### 리뷰 ① (개선 전, `bench/review-before`) 회차
- `git checkout bench/review-before` → `./gradlew bootJar` → 기동. 첫 기동에서 ddl-auto=update가 `shops`에 `review_count`, `rating_sum`, `average_rating`을 추가한다.
- 기존 행의 DECIMAL 컬럼은 NULL이라 `reset-round.sql` 하단 `UPDATE shops SET ... = 0`을 주석 해제해 실행해야 갱신 시 NPE가 나지 않는다.
- **파일럿 1분 먼저.** `-e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=1m -e THRESHOLDS=off -e TAG=before-pilot`로 시작해 `lock_deadlocks` 증가분이 두 자릿수 이상 나오는 VUS·SHOP_POOL 을 찾는다. 안 나오면 SHOP_POOL 을 1로, 그래도 안 나오면 VUS 를 100으로 올린다. 확정한 값을 ①~③ 본측정 세 줄에 동일하게 넣는다.
- closed model 에서는 ①의 데드락 롤백(500)이 빠르게 끝나 처리량이 오히려 높게 보일 수 있다. 처리량은 반드시 `reviews_created`(2xx) 기준으로 비교하고 `iterations` 를 쓰지 않는다.
- **판정은 threshold가 아니다.** `http_req_failed`가 1% 미만이어도 데드락은 발생한다. 근거는 `lock_deadlocks` 증가분, k6 `reviews_5xx` 건수(≈ 데드락 수), `verify.sql` 4-a)의 유실 행, `SHOW ENGINE INNODB STATUS`의 LATEST DETECTED DEADLOCK 1회 캡처다.
- 이 회차에서 `verify.sql` 2)·2-b)·2-c)는 `shop_review_stats`를 갱신하지 않으므로 전부 불일치로 나온다. 근거로 쓰지 않는다.
- 이 브랜치에서 main의 정합성 테스트(`ReviewStats*Test`)는 실패한다. 벤치마크 전용 브랜치다.

## 4. 서버 측 지표 폴링 (k6가 못 재는 것)

5초 간격 CSV. 두 풀의 queued·active, 거절·실패 카운터, Hikari 대기를 함께 본다. (Git Bash, 종료는 Ctrl+C)

```bash
S=http://<서버IP>:8080
m() { curl -s "$S/actuator/metrics/$1" | grep -o '"value":[0-9.E+-]*' | head -1 | cut -d: -f2; }
echo "time,insert_queued,insert_active,stats_queued,stats_active,order_rejected,stats_rejected,stats_failed,hikari_active,hikari_pending" > server-metrics.csv
while true; do
  echo "$(date +%T),$(m 'executor.queued?tag=name:insertTaskExecutor'),$(m 'executor.active?tag=name:insertTaskExecutor'),$(m 'executor.queued?tag=name:reviewStatsExecutor'),$(m 'executor.active?tag=name:reviewStatsExecutor'),$(m omp.order.async.rejected),$(m omp.review.stats.rejected),$(m omp.review.stats.failed),$(m hikaricp.connections.active),$(m hikaricp.connections.pending)" | tee -a server-metrics.csv
  sleep 5
done
```

- **완료 시점** = k6 종료 후 해당 풀의 `queued=0`이고 `active=0`이 된 첫 시각. 이후 `COUNT(*)`가 변하지 않아야 한다.
  큐 크기가 100이라 "큐 소진 시간"은 1초 미만이고 지표로서 의미가 거의 없다. 대신 **거절 건수**(증가분)와 **완료 시점까지의 지연**을 기록한다.
- `hikari_pending`이 0 이상 지속되면 단일 커넥션 풀(20)을 조회·INSERT가 공유하며 경쟁하는 구간이다. 지연 해석에 기록한다.

## 5. 결과 읽는 법

| 지표 | 의미 |
|---|---|
| `iterations` | **시도** 수. 실패·503 포함. 접수량이 아니다 |
| `orders_accepted` / `reviews_created` | 검증 통과 응답 수 (202+Location / 2xx). **접수량** |
| 리뷰 15분 처리량 | `reviews_created` ÷ 15분 (closed model). ①②③ 같은 VUS 에서 비교 |
| `orders_rejected` | 503 수. 서버 `omp.order.async.rejected` **증가분**과 같아야 한다 |
| `orders_failed_other` / `reviews_5xx` | 그 외 실패. 리뷰 ①에서 5xx = 데드락 롤백 |
| `completed_orders` (verify 3) | 커밋 완료량. **완료 시점 이후** 값 |
| `http_req_duration` p95/p99 | 주문 sync는 저장 완료까지, async는 접수까지의 응답 시간 |
| `dropped_iterations` | **0**이어야 "초당 N건 유입 유지" 주장 성립. 쌓이면 `MAX_VUS` 부족 또는 서버 포화 |
| `checks` | strict 회차에서 100% |

해석 노트:
- 바닥값은 참고선이다. **"API p95 − ping p95 = 서버 처리 시간"** 같은 뺄셈은 하지 않는다 (서로 다른 분포의 백분위수는 뺄 수 없다). 서버 내부 구간 시간은 별도 계측이 필요하다.
- 비동기 주문에서 503이 나오면 insertTaskExecutor 포화 → 접수 거절(백프레셔). "측정 조건에서 503 없이 유지한 유입률"이 접수 용량이다. dropped_iterations·p95·다른 오류·완료 결과를 함께 확인한다. (CallerRunsPolicy는 제거됨. 포화 시 톰캣 스레드가 몰래 INSERT하는 구간은 없다.)
- 유실 판정: `orders_accepted == completed_orders`(총건수)는 기본 점검이다. 누락과 중복이 상쇄될 수 있으므로 요청별 대조는 3단계 과제.
- 리뷰 ②·③: `verify.sql` 2)·2-b)·2-c) 0행 + `omp.review.stats.rejected`·`failed` 증가분 0. 거절·실패가 N이면 2)에 N건 불일치가 남아야 한다 (재처리 없음 = **불일치 지속**, "지연"이 아니다).
- 리뷰 ①: `reviews_5xx` ≈ lock_deadlocks 증가분, 4-a) 유실 행 수, ①→② 응답 시간 차이.
- 주문 측정은 POST 응답만 본다. SSE 연결·폴링을 포함한 전체 사용자 흐름의 성능은 아니다. 결과 표에 범위를 명시한다.

## 6. 매 측정 기록 환경 표 (결과 문서에 복사)

```
| 항목 | 값 |
|---|---|
| 측정일시 / 회차 / TAG | 2026-XX-XX / N회차 (3회 중) / 예: B-r1 (주문 A~D, 리뷰 before·sync·async) |
| 커밋 SHA / 브랜치 | main <sha> 또는 bench/review-before <sha> |
| 서버 | 노트북 모델, CPU, RAM, 전원 연결+최고 성능 모드, 클럭(HWiNFO) |
| 네트워크 | 서버 유선/무선, 부하기 유선 |
| JVM | 21.x, -Xms2g -Xmx2g, GC 종류 |
| MySQL | 8.0.36, innodb_buffer_pool_size, innodb_flush_log_at_trx_commit, 앱과 동거 |
| 커넥션 풀 | HikariCP 20 (단일 풀, 조회·INSERT 공유) |
| 스레드 풀 | omp.executor.* (기본 insert 10/30/q100, reviewStats 10/20/q2000, sse 10/30/q200), insert·reviewStats 거절 정책 Abort, sse CallerRuns |
| 리뷰 통계 모드 | omp.review.stats.mode = sync / async (① 브랜치는 해당 없음) |
| 카운터 (시작 전 → 종료 후) | omp.order.async.rejected, omp.review.stats.rejected, omp.review.stats.failed |
| 데드락 카운터 (시작 전 → 종료 후) | lock_deadlocks N → M (차이 = 발생 건수), Innodb_row_lock_waits/time |
| 데이터 | seed.sql (users 10만, shops 1천, carts 10만), 회차마다 reset-round.sql (워밍업 후) |
| 부하 | k6 버전, SCENARIO/MODEL, RATE 또는 VUS, DURATION, SHOP_POOL, MAX_VUS, THRESHOLDS, dropped_iterations |
| 바닥값 | /ping p95 = X ms (참고선) |
| 완료 시점 | k6 종료 후 queued=0·active=0 까지 N초, COUNT 정지 확인 |
```

## 7. 흔한 함정

- **절차 순서**: reset을 워밍업 앞에 두면 워밍업 쓰기가 남아 완료량·접수량 비교가 깨진다. 3절 순서대로.
- **카운터는 누적값**: actuator 카운터와 lock_deadlocks는 기동 후 누적. 반드시 시작 전 값을 기록하고 증가분을 쓴다.
- **부하기 모니터링**: k6 실행 중 데스크탑 CPU 90% 초과 시 부하기 병목 → 결과 무효.
- **노트북 온도**: HWiNFO 등으로 클럭 기록, 스로틀링 회차는 표시.
- **IP 변동**: WiFi↔유선 전환 시 IP 바뀜. 회차마다 확인.
- **① 회차 준비**: shops 통계 컬럼 0 초기화(reset 하단) 없이 기동하면 NPE로 전부 500이 난다. 데드락과 구분되지 않으므로 반드시 먼저 실행.
- **① 회차 판정**: threshold 통과·실패로 판정하지 않는다. lock_deadlocks 증가분과 5xx 건수로.
- **closed model 처리량 착시**: 실패 응답이 빨리 돌아오면 iterations 가 부풀어 보인다. 처리량은 2xx(`reviews_created`)만 센다.
- **테스트 실행 금지(OMP)**: 통합 테스트는 마스터 테이블을 전부 지운다. 8절대로 OMP_TEST에서만.
- **actuator 노출**: 현재 `management.endpoints.web.exposure.include=*` — 벤치마크 편의용이므로 외부 배포 시 축소.
- **테이블명 대소문자**: Windows MySQL은 대소문자 무시. 서버를 Linux로 옮기면 소문자 테이블명 기준으로 SQL 확인.

## 8. 테스트 실행 (측정과 무관, 코드 수정 검증)

```bash
OMP_DB_PASSWORD=<비번> ./gradlew test --console=plain
```

- 프로필 `test` → `src/test/resources/application-test.properties` → DB **`OMP_TEST`** (`createDatabaseIfNotExist=true`, `ddl-auto=create`).
- `TestFixtures.resetAndSeed`는 users/shops/carts를 전부 지우기 전에 `DATABASE()`가 `OMP_TEST`인지 확인하고 아니면 예외로 중단한다. 프로필 파일이 없으면 기본 설정(OMP)으로 붙으므로 이 가드가 seed 데이터를 지킨다.
- `bench/review-before`에서는 `ReviewStats*Test`가 실패한다 (통계 테이블을 쓰지 않는 설계). 그 브랜치는 벤치마크 전용이다.
