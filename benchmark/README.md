# k6 재측정 가이드 (Order-Me-Plz)

부하 생성기 = **데스크탑(유선)**, 서버+MySQL = **노트북(유선 연결)** 기준. k6는 데스크탑에만 설치한다.

```bash
winget install k6 --source winget   # 또는 choco install k6
k6 version                          # 환경 표에 기록
```

선행 문서: [REVIEW-2026-09-08.md](REVIEW-2026-09-08.md) (측정 설계 검토), [STEP1-2026-09-19.md](STEP1-2026-09-19.md) (측정 전 코드 수정).
2026-09-22 리뷰 포트폴리오 검토 반영: 책임 분리와 트랜잭션 분리의 구분, 결함별 재현 계획, 두 장비 유선 환경, 지표·판정 기준을 정리했다. 리뷰 원인 재현 테스트는 2026-09-26 완료(2절 "리뷰: 측정 전 확정할 항목"), 본측정은 아직이다.
2026-09-24 리뷰 통계 반영 방식을 요구사항 우선순위로 확정했다: **② 같은 트랜잭션 채택(기본 모드 sync), ③ 비동기는 비교군.** 근거와 검증 기준은 2절 "리뷰: 설계 판단 기준".

---

## 0. 빌드·기동 (서버 노트북)

| 항목 | 값 |
|---|---|
| JDK | 21 (`java -version` 확인. 빌드 toolchain도 21) |
| 빌드 | `./gradlew bootJar` (Windows PowerShell은 `.\gradlew.bat bootJar`). Gradle wrapper(8.11.1)가 레포에 포함되어 JDK 21만 있으면 된다. 서버 노트북에서는 테스트(`test`·`build`)를 돌리지 않는다 |
| 설정 | 리포 루트의 `.env` (`cp .env.example .env`, 추적하지 않음). DB 접속, Hikari P, Tomcat 스레드, 리뷰 통계 모드, 세 executor 풀. 바꾼 뒤 **재시작만** 하면 된다(재빌드 불필요). 우선순위: 기동 인자 > OS 환경변수 > `.env` > `application.properties` 기본값 |
| 기동 위치 | **리포 루트** (`.env`를 상대 경로로 읽는다. 다른 곳에서 기동하면 조용히 기본값으로 뜬다) |
| 리뷰 통계 모드 | `OMP_REVIEW_STATS_MODE` = `sync`(기본, 채택) / `async`(비교군) |
| 힙 | `-Xms2g -Xmx2g` 고정 (리사이즈 노이즈 제거) |

```bash
./gradlew bootJar
cp .env.example .env      # 최초 1회. DB 비밀번호 등 수정
java -Xms2g -Xmx2g -jar build/libs/OrderMePlz-0.0.1-SNAPSHOT.jar
# 리뷰 ③ 회차만: .env 에 OMP_REVIEW_STATS_MODE=async (또는 기동 인자 --OMP_REVIEW_STATS_MODE=async)
```

기동 후 실제 적용값 확인 (환경 표에 기록). 로그 레벨이 warn이라 기동 완료 로그는 안 보이므로 `/ping`으로 확인한다.
```bash
S=http://localhost:8080
m() { curl -s "$S/actuator/metrics/$1" | grep -o '"value":[0-9.E+-]*' | head -1 | cut -d: -f2; }
echo "P=$(m hikaricp.connections.max) insert=$(m 'executor.pool.core?tag=name:insertTaskExecutor')/$(m 'executor.pool.max?tag=name:insertTaskExecutor')/q$(m 'executor.queue.remaining?tag=name:insertTaskExecutor') stats=$(m 'executor.pool.core?tag=name:reviewStatsExecutor')/$(m 'executor.pool.max?tag=name:reviewStatsExecutor') tomcat=$(m tomcat.threads.config.max)"
```
큐 크기(`queue.remaining`)는 큐가 빈 기동 직후에만 설정값과 같다.

## 1. 무엇을 무엇과 비교하는가

| 측정 | 브랜치 | 기동 옵션 | 스크립트·옵션 | 목적 |
|---|---|---|---|---|
| 바닥값 | main | | `00-network-floor.js` | `/ping`. 해석 참고선. 1회 |
| 주문 · 동기 (비교 기준) | main | | `01-order-api.js -e MODE=sync` | 회차 B·A·S·C (2절 "주문: 회차 구성"). 같은 빌드에 두 엔드포인트 공존 |
| 주문 · 비동기 접수 | main | | `01-order-api.js` (MODE=async) | 회차 B·A·S·C. 헤드라인은 S(스파이크 흡수). 초안 [PORTFOLIO-1-2-draft.md](PORTFOLIO-1-2-draft.md) |
| 리뷰 ① shops 통계 + 같은 트랜잭션 | `bench/review-before` | | `02-review-api.js -e MODEL=closed -e TAG=before -e THRESHOLDS=off` | 데드락·유실 재현 |
| 리뷰 ② 별도 통계 + 같은 트랜잭션 **(채택)** | main | (기본) | `02-review-api.js -e MODEL=closed -e TAG=sync` | ①→② = 모델 분리 + 원자 갱신의 결합 효과. 채택 검증(2절) |
| 리뷰 ③ 별도 통계 + AFTER_COMMIT 비동기 (비교군) | main | `OMP_REVIEW_STATS_MODE=async` | `02-review-api.js -e MODEL=closed -e TAG=async` | ②→③ = 트랜잭션 분리 + 비동기 실행의 결합 효과 (응답 지연 이득 vs 반영 지연·누락 경로). 채택 여부를 가르지 않음 |

- 주문 전/후는 checkout 없이 같은 서버에서 URL만 바꾼다. 단, 동기 응답은 **저장 완료**까지, 비동기 응답은 **접수**까지라 계약이 다르다. 접수 지연과 커밋 완료량을 따로 기록하고 "저장 속도 개선"으로 쓰지 않는다.
- `bench/review-before`는 **최신 main에서 분기**해 `Shop`의 통계 필드 3개와 `ReviewService.saveReviewBy` **두 파일만** 바꾼 재구성 브랜치다(2026-09-26 재분기: 이전 브랜치는 9/22 main 기준이라 이후의 계측·캐시·기본 모드·주문 수정이 빠져 main과 17개 파일이 달랐다). 애플리케이션의 스레드 풀·검증·예외 처리·설정·계측은 main과 같지만, ①→②에서는 통계 저장 위치와 갱신 방식이 함께 바뀐다. 모델 분리만의 성능 효과라고 해석하지 않는다. main이 바뀌면 같은 방식으로 다시 분기해 두 파일 차이를 유지한다. 옛 05ad2d8 기반 구성은 CallerRuns 풀·Spring Retry·writerId 미할당이 섞여 비교를 오염시키므로 쓰지 않는다.
- 리뷰는 ②를 채택했다. ①→②에서 데드락·갱신 유실이 모델 분리와 원자 UPDATE로 해결되므로, ③의 분리는 결함 해결 수단이 아니라 별도의 선택이다. 그 선택은 응답 시간이 아니라 요구사항 우선순위(2절)로 판단했고, 측정은 ②가 그 요구를 만족하는지 검증하고 ③의 이득·대가를 기록하는 데 쓴다.
- 옛 nGrinder 수치와는 도구가 다르므로 비교하지 않는다.

## 2. 측정 유형

1. **고정 rate** (`SCENARIO=fixed`, 기본): "초당 N건 유입 시 p95/p99와 에러율". 전/후 **모두 감당 가능한 rate**로 고정해야 비교가 성립한다.
2. **고정 VU** (`MODEL=closed`, 리뷰 ①②③): VUS명이 응답을 받은 뒤 다음 요청을 보낸다(nGrinder vUser 방식). VU 수를 고정하며, 응답 시간과 스크립트 실행 시간에 따라 실제 유입률은 달라진다. 리뷰는 "같은 VU 수에서 성공 응답 수·p95·실패·정합성"을 비교한다. "같은 유입률에서 응답 지연 개선"을 주장하려면 ②·③에 같은 RATE의 open model을 추가한다.
3. **용량** (주문 A): W 비율 계단마다 `SCENARIO=fixed`를 4분씩 따로 돌리는 계단식 고정 run. k6 요약은 run 전체를 집계하므로 계단을 run 하나로 합치지 않는다. 판정 기준은 "주문: 회차 구성" 표. `SCENARIO=ramp`는 빠른 탐색용 보조로만 쓴다.
4. **포화** (`SCENARIO=saturate`, 풀 크기 파일럿 전용): VUS명이 쉬지 않고 요청해 서버를 포화시킨 상태의 처리량을 잰다. 아래 "풀 크기 산정" 1단계에서만 쓴다.
5. **스파이크** (`SCENARIO=spike`, 주문 S 회차): 평상시 → 순간 급증 → 평상시. 구간(phase)별 지연·거절이 요약에 따로 나온다.

### 주문: 요구값과 근거 (2026-09-26 확정)

비동기 접수의 역할은 **순간 유입(쿠폰 오픈·푸시 발송 직후)을 흡수**하는 것이다. 정상 상태에서는 두 방식 모두 같은 DB 처리량에 묶이고, 비동기의 확정 시간(접수 + 저장)은 동기 응답보다 짧아지지 않는다. 지속 유입이 처리량을 넘는 저녁 피크는 비동기로 해결되지 않는 용량 문제다(A 회차로 여유를 확인하고, 중소 앱 목표치에서는 저녁 피크가 용량을 넘지 않아 C 회차는 보류).

| 요구 | 값 | 근거 (서비스 데이터가 아닌 가정임을 명시) |
|---|---|---|
| 접수 응답 | 성공 응답 p95 ≤ 200ms | 원 포폴 목표(평균 200ms)를 p95로 강화. 응답 시간 기준(Nielsen: 0.1초 즉각, 1초 흐름 유지, 10초 주의 한계)에 비춰 모바일 왕복 지연을 더해도 1초보다 한참 아래. LAN에서 잰 서버 측 값이다 |
| 저장 완료 | 제출 → 커밋 p99 ≤ 30초 | 10초를 넘으면 진행 표시가 필요하며 SSE로 "처리 중"을 보여준다. 동기 경로의 커넥션 대기 한도(Hikari 기본 30초)와 같아 "동기라면 실패로 끝났을 시점 안에 저장한다"는 비교 기준이 된다. 상태 TTL·SSE 타임아웃(5분)의 1/10. 줄이면 흡수량이 줄고(10초 → 큐 1/3), 늘리면 대기가 길어진다 |
| 초과분 | 즉시 429 + Retry-After | 허용 지연 안에 저장할 수 없는 분량은 받지 않는다. 응답 코드는 아래 "거절 응답 코드"(2026-09-28, 처음에는 503) |

### 주문: 트래픽 목표치 (2026-09-29 확정, 중소 배달앱 규모)

처음 설계는 부하를 모두 잰 용량 W의 배수로만 정해 "서비스가 초당 몇 건을 받아야 하는가"가 없었다. 규모를 **중소 배달앱**으로 정하고 공개 자료에서 목표치를 계산했다. 서비스 데이터가 아니라 가정이며, 가정은 표에 드러낸다.

| 항목 | 값 | 계산·근거 |
|---|---|---|
| 규모 | MAU 약 345만 명 | 땡겨요 수준(와이즈앱·리테일 2025년 10월: 배민 2,170만, 쿠팡이츠 1,230만, 요기요 444만, 땡겨요 345만) |
| 월 주문 | 약 1,700만 건 | MAU × 월 5건. 배민 월 주문 1억 건(2021년 8월 첫 돌파) ÷ MAU 약 2천만의 비율이 같다고 가정 |
| 평상시 목표 (저녁 피크 1시간) | **초당 약 40건** | 하루 약 57만 건 중 70%가 17~21시에 몰리고(배달앱 주 이용 시간대 설문 69%를 주문 비중으로 가정), 피크 1시간은 4시간 평균의 1.5배로 가정 |
| 이벤트 목표 (선착순 쿠폰·푸시 직후 60초) | 10만 명: **초당 약 1,707건** / 20만 명: **초당 약 3,373건** | 평상시 40 + 참여자 ÷ 60초. 참여자는 MAU의 약 3% / 6%로 가정(근거 데이터 없음, 두 단계를 모두 잰다) |

- **왜 비동기인가(가설):** 평소에는 초당 수십 건이라 서버 한 대로 충분하지만, 이벤트 직후에는 40~85배가 몰린다. 드문 순간을 위해 평소 서버를 늘려 둘 수 없으니 순간 유입을 흡수해야 한다. 10만 명은 이 노트북의 동기 용량(약 2,360건/s) 안이고, 20만 명은 동기·비동기 용량을 모두 넘는다. 10만 명은 "어디까지는 동기로 충분한가"의 대조군이다.
- **평상시 처리량은 목표보다 한참 크다.** 비동기 W(약 2,620~2,950건/s)는 평상시 목표의 약 65~75배다. W를 더 올리는 배치 저장은 목표 달성에 필요하지 않아 개선 과제로 보류한다(W 편차 논의는 P2 결과 README 8절).
- 출처: [바이라인네트워크, 배민 15주년(2025-06-30)](https://byline.network/2025/06/30_baemin/) 월 주문 1억 건·입점 30여만 곳, [전자신문, 배달앱 MAU(2025-11-25)](https://www.etnews.com/20251125000090) 와이즈앱·리테일 2025년 10월, [아이보스, 트렌드모니터 배달앱 조사](https://www.i-boss.co.kr/ab-74668-934) 주 이용 시간대 17~21시 69%(원문 접근 불가로 수치 미확인).

### 풀 크기 산정 (주문·리뷰 공통, 본측정 전 확정)

서버 노트북: **물리 4코어 / 논리 8 / RAM 16GB.** 앱(JVM)과 MySQL이 같은 CPU를 나눠 쓴다.

원칙
- 풀 크기는 시나리오(평상시·저녁 피크·쿠폰 이벤트)마다 바꾸지 않는다. **하나의 고정 설정**으로 세 상황을 모두 돌려, 그 설정이 모두 감당하는지 검증한다.
- 병목(DB)에서 거꾸로 정한다: 커넥션 총량 P → insert 워커 k → 저장 처리량 W(측정) → insert 큐 Q.
- 공식은 출발점이고 확정은 파일럿 측정으로 한다. 확정값은 `.env`와 `application.properties` 기본값에 반영해 모든 본측정(동기 비교군·리뷰 포함)에 같은 값을 쓴다.

| 단계 | 값 | 출발점과 근거 | 확정 방법 |
|---|---|---|---|
| 1 | 커넥션 총량 **P** | HikariCP 문서(About Pool Sizing)의 출발 공식 `물리 코어 × 2 + 유효 디스크 수` = 4 × 2 + 1 = 9 → **10**. 하이퍼스레딩 논리 코어는 세지 않는다(HikariCP 기본값도 10). 코어의 2배인 이유는 한 트랜잭션이 fsync·네트워크를 기다리는 동안 다른 트랜잭션이 CPU를 쓰게 하기 위해서이고, 그보다 많으면 컨텍스트 스위칭과 락·버퍼 경합만 는다. 이 노트북은 앱과 DB가 CPU를 나눠 쓰므로 실제 최적은 더 작을 수 있다 | 파일럿 P1: 동기 포화에서 P = 5 / 10 / 15 / 20. **최대 처리량의 95%에 도달하는 가장 작은 P** |
| 2 | 접수 경로 몫 | Little의 법칙: 동시 필요 커넥션 = 유입률 × 커넥션 점유 시간. 스파이크(2W) 기준 예: 3000/s × 1ms × 여유 1.5 ≈ 4~5 | 따로 설정하지 않는다. P − k로 남는 몫이며 3단계의 접수 p95 조건으로 검증한다 |
| 3 | insert 워커 **k** (core = max) | P − 접수 몫. 워커는 대부분 DB를 기다리므로 스레드 수 공식(코어 × (1 + 대기/계산))상 코어보다 많아도 되지만, 커넥션보다 많으면 커넥션을 기다릴 뿐이다. 상한은 코어가 아니라 워커에게 배정된 커넥션 수다 | 파일럿 P2: 비동기 포화에서 k = 4 / 6 / 8. **W가 더 오르지 않으면서 202 응답 p95 ≤ 200ms인 가장 작은 k**. 이때의 접수 처리량이 W |
| 4 | insert 큐 **Q** | `W × 30초 × 0.8`. FIFO 큐의 최대 대기 ≈ Q ÷ W(Little의 법칙)이므로 저장 완료 30초 한도를 지키는 상한은 30W. 0.8은 스파이크 중 W 하락(접수 경로와의 경쟁, GC, fsync 편차)과 INSERT 시간 여유 | 계산값 |

core = max로 두는 이유: ThreadPoolExecutor는 큐가 가득 차야 max까지 스레드를 늘리므로, 큐가 크면 max 설정은 의미가 없다.

| 그 밖의 값 | 결정 | 근거 |
|---|---|---|
| Tomcat 스레드 | 기본 200 | 동기에서는 커넥션 앞의 대기열 역할이고, 스파이크 때 소진되는 것 자체가 비교 대상이다. 비동기 접수에 필요한 수는 약 3000/s × 20ms = 60 |
| 한 스레드의 커넥션 동시 점유 | 1개 (확인) | CallerRuns 제거 후 모든 경로가 커넥션 1개만 쓴다. 스레드마다 커넥션을 여러 개 기다리는 풀 교착 조건이 없다 |
| reviewStats 워커 (③ 비교군) | core = max = P/2, queue 2000 | 핫 가게 행 3개에서는 동시에 3건만 진행되고 나머지는 락을 기다리며 커넥션을 쥔다. 커넥션 독점을 막기 위해 절반 이하 |
| sse 풀 | 현행 10/30/q200 | DB를 쓰지 않는 이벤트 전송이라 커넥션 예산과 무관 |
| JVM 힙·메모리 | `-Xms2g -Xmx2g` | RAM 16GB에서 병목이 아니다. 큐 Q가 수만 건이어도 수십 MB. `innodb_buffer_pool_size`는 시드와 회차 데이터가 들어가는지 확인·기록 |

**각 값이 겨냥하는 시나리오:** 커넥션 P와 워커 k는 저녁 피크(지속 처리량 W), 큐 Q는 쿠폰 이벤트(순간 흡수), 평상시는 여유와 비동기의 비용을 확인한다.

**파일럿 절차 (본측정 전 1회).** 값은 서버 노트북의 `.env`나 기동 인자로 바꾼다(재빌드 없이 재시작, 기동 인자가 `.env`보다 우선). 2026-09-26~27 파일럿은 데스크톱에서 SSH로 서버를 기동하며 기동 인자로 넣었고, 적용값을 actuator(`hikaricp.connections.max`, `executor.pool.core`)로 매 회차 확인했다. run마다 서버 재시작 → 워밍업 1분 → `reset-round.sql` → 3분 측정. 측정 중 4절 폴링과 mysqld·java CPU 사용률을 함께 기록한다(처리량이 멈추는 원인이 CPU 공유인지 판단).

```bash
S=http://<서버IP>:8080; OUT=benchmark/results

# P1. 커넥션 총량 — 서버: .env 의 OMP_HIKARI_MAX_POOL_SIZE=<5|10|15|20> 만 바꿔 4번 기동
#   java -Xms2g -Xmx2g -jar build/libs/OrderMePlz-0.0.1-SNAPSHOT.jar   (0절 확인 명령으로 P 적용 확인)
#   VUS 64: 최대 P(20)보다 충분히 커서 풀이 항상 포화되고, Tomcat 200보다 작아 Tomcat이 제한 요인이 되지 않는다.
k6 run -e BASE_URL=$S -e MODE=sync -e SCENARIO=saturate -e VUS=64 -e DURATION=3m -e THRESHOLDS=off -e TAG=P1-p<P> -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
#   기록: orders_accepted ÷ 180초 = 처리량, p95, hikari_pending, CPU%.

# P2. insert 워커 — 서버: P는 P1 확정값, 큐는 100으로 작게 둬서 접수량 = 완료량이 되게 한다
#   .env: OMP_HIKARI_MAX_POOL_SIZE=<P>, OMP_EXECUTOR_INSERT_CORE=<k>, OMP_EXECUTOR_INSERT_MAX=<같은 값>, OMP_EXECUTOR_INSERT_QUEUE=100
#     (또는 기동 인자 --spring.datasource.hikari.maximum-pool-size=<P> --omp.executor.insert.core=<k> --omp.executor.insert.max=<k> --omp.executor.insert.queue=100)
#   k 후보 4/6/8은 P = 10 기준(P의 약 0.4·0.6·0.8배, 나머지는 접수 경로 몫).
#   RATE: 비동기 용량의 1.5배(2026-09-27 정정). 처음에는 P1 처리량(동기 용량)의 1.5배로 잡았으나 비동기 용량이 동기의 약 65%라 2.3배 과부하가 됐다.
#         수정 전 코드로 잰 비동기(SPLIT) 용량 약 1,500/s → 2,250/s. 503이 나와야 포화된 것이며, 503이 없으면 RATE를 올린다.
k6 run -e BASE_URL=$S -e MODE=async -e RATE=<P1 처리량 × 1.5> -e DURATION=3m -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=P2-k<k> -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
#   기록: orders_accepted ÷ 180초 = W, order_accepted_duration p95(202만), order_rejected_duration p95, 503 수, hikari_pending.

# 확정: hikari = P, insert core = max = k, insert queue = Q, reviewStats core = max = P/2 를
#   서버 .env 에 넣고, .env.example 과 application.properties 기본값에도 같은 값으로 맞춰 커밋한다(커밋 SHA만으로 설정 재현).
```

**P1 결과 (2026-09-26): 동기 기준 P = 10.** (최종 P는 비동기까지 잰 P2에서 20으로 확정했다. 아래 "P2 결과") 회차마다 직전 부하 종료 후 15분 휴식, 충전기 연결, 노트북 클럭 기록 조건에서 쟀다(상세·무효 회차는 [results/2026-09-26-P1/README.md](results/2026-09-26-P1/README.md)).

| P | 처리량/s (회차별) | 평균 | 최대 평균 대비 | 노트북 CPU (mysqld / java) |
|---|---|---|---|---|
| 5 | 1,999 | 1,999 | 84.6% | 76% (43% / 27%) — 커넥션 대기 55건, CPU 여유 |
| **10** | 2,266 · 2,363 | **2,314** | **97.9%** | 96~97% (58% / 32~33%) |
| 15 | 2,398 · 2,329 | 2,364 | 100% | 96~97% (58% / 32~33%) |
| 20 | 2,361 | 2,361 | 99.9% | 96% (58% / 32%) |

- P=10부터 노트북 CPU가 포화돼 처리량이 평탄하다. P=10·15·20의 평균 차이(2% 이내)는 같은 P 반복 편차(3~4%)보다 작다. 동기 주문의 한계는 커넥션 수가 아니라 앱과 MySQL이 나눠 쓰는 CPU이고, CPU의 약 60%를 MySQL이 쓴다.
- 규칙(최대 평균 2,364의 95% = 2,245 이상인 가장 작은 P)으로 P = 10. 출발 공식(4 × 2 + 1)과 같다.
- 전 회차 실패 0, 부하 발생기 판정 통과, 노트북 클럭(기본 2.8GHz 대비) 부하 중 112%(P=5는 116%)로 회차 간 동일.

**P2 대비 규칙 (P2 측정 전 확정, 2026-09-26).** P는 동기로 정했지만 서버 전체가 나눠 쓰는 하나의 커넥션 풀이다. 비동기에서는 주문 하나가 접수(검증 SELECT, 요청 스레드)와 저장(INSERT, insert 워커)에서 커넥션을 두 번 쓰고, 워커 k개가 커넥션을 쥐면 접수 경로에는 P − k개가 남는다. 그래서 P2가 "P=10이 비동기에도 충분한가"의 검증을 겸한다. **P=10에서 어떤 k도 "W 정체 + 202 p95 ≤ 200ms"를 만족하지 못하면 P=15로 P2를 다시 돌리고, 만족하는 더 작은 P를 확정한다.** P1에서 P=15는 처리량 손해가 없었다(P=10과 차이가 반복 편차 안).

**P2 첫 시도에서 찾은 결함과 수정 (2026-09-27).** 3,470/s 회차가 무효가 되면서, 비동기 접수가 트랜잭션(커넥션 획득 → 검증 SELECT)을 마친 뒤에야 큐가 찼는지 알고 거절마다 WARN 로그를 남긴다는 것을 찾았다. 과부하에서 거절 비용이 받은 주문까지 느리게 해 "초과분 즉시 503"을 지키지 못했다(503 p95 0.3~0.8초). 트랜잭션 전 대기 자리 예약(`AsyncOrderAdmission`, 자리 수 = 큐 용량)과 건별 로그 제거로 고쳤다. 같은 결과에서 비동기(SPLIT) 용량이 동기의 약 65%라는 것도 확인해, 트랜잭션 분리 비용을 가리는 설정(`omp.order.async.transaction=split|single`)을 추가했다. 회차별 결과와 판정은 [results/2026-09-26-P2/README.md](results/2026-09-26-P2/README.md).

**P2 결과 (2026-09-27): `single` 채택, P = 20, k = 20, W ≈ 2,940건/s, Q = 70,000.** 수정 후 코드로 두 구성 × P·k 격자 10회차(2,700/s)와 single 한계 7회차(3,600 → 3,000/s)를 쟀다. 상세·무효 회차는 [results/2026-09-26-P2/README.md](results/2026-09-26-P2/README.md).

| 구성 | P·k | W/s | 202 p95 | 비고 |
|---|---|---|---|---|
| split | 10·4 / 10·6 / 10·8 | 1,401 / 1,598 / 1,840 | 31 / 58 / 68ms | 워커를 늘리면 접수 몫 커넥션(P − k)이 줄어 접수가 느려짐 |
| split | 15·6 / 15·10 | 1,697 / 1,633 | 53 / 345ms | |
| single | 10·6 / 10·8 / 10·10 | 1,859 / 2,173 / 2,187·2,210 | 9 / 26 / 54~86ms | 접수가 커넥션을 쓰지 않아 k를 P까지 올릴 수 있음 |
| single | 15·15 | 2,627(2,700/s, 포화 전) · 2,760 · 2,728(3,000/s) | 19~41ms | 3,000/s 3회 중 1회 응답 붕괴 |
| **single** | **20·20** | **2,939 · 2,947**(3,000/s의 98%) | 29~31ms | 2회 모두 안정 |

- **구성:** 같은 k에서 single의 W가 16~18% 높고 접수 응답이 빠르다(주문당 MySQL 명령 15 → 9). split의 장점은 잘못된 주문을 즉시 400으로 알리는 것 하나이고, 검증과 저장 사이(큐 대기 최대 약 Q ÷ W초)에 바뀐 상태를 다시 검증하지 않는 틈이 있다. 관심사 분리는 코드 구조(`OrderValidator`, `AsyncOrderAdmission`, `AsyncOrderHandler`, `AsyncOrderProcessor`)로 유지하고 트랜잭션 경계만 저장 단계 하나로 둔다. 기본값 `single`, split은 비교군.
- **P·k:** P만 늘리는 효과는 +3~6%로 작고, 효과는 저장 워커 수에서 나온다. 3,000/s 회차의 주문당 redo fsync가 k=10 1.21회, k=15 0.39~0.42회, k=20 0.32~0.35회로, 동시 커밋이 많을수록 디스크 기록을 묶는 경향이 보인다(확정 아님).
- **선정 규칙:** P1과 같은 "최대 평균 W의 95% 이상인 가장 작은 설정". 최대 평균 2,943건의 95% = 2,796건 → P=15·k=15(평균 2,744건, 93.2%)는 미달 → **P=20·k=20**. 동기 최대(2,364건)보다 약 24% 높다.
- **사전 규칙과 다른 선택이다.** 위 "P2 대비 규칙"을 글자대로 적용하면 P=10에서 k=8 → 10이 정체(2,173 → 2,187)하므로 P=10·k=8이 된다. 그 규칙은 "P를 10 넘게 늘려도 처리량이 늘지 않는다"는 동기 결과를 전제로 했지만, 비동기 single에서는 P·k를 20까지 늘리면 +35%로 전제가 틀렸다. 그래서 규칙을 결과에 맞춰 고친 게 아니라, P1에서 쓴 95% 규칙을 비동기 격자 전체에 같은 방식으로 적용했다.
- **W의 한계:** 2,943건/s는 노트북 유입 한계(약 3,000건/s)에 닿은 값이다. 채택 설정으로 부하만 올려 보니 3,300·3,600/s는 저하, 4,000/s는 붕괴였다(결과 README 6절, 거절 응답 503). 4,000/s 붕괴 원인은 503마다 끊기는 연결로 확인했다(아래 "거절 응답 코드"). 429로 다시 재 보니(결과 README 8절) 3,000/s도 안정 구간이 아니었다: P=20·k=20 6회 중 2회는 저장량이 2,695·2,708건으로 떨어지고 응답이 0.6초대로 느려졌고, 2,850/s는 2회 모두 정상, 2,700/s는 2회 중 1회 나쁨이었다. 저장 용량은 한 점이 아니라 약 2,620~2,950건/s 구간이며, 같은 부하에서 나쁜 회차는 커밋이 덜 묶여(주문당 redo fsync 0.35 → 0.57~0.69회) MySQL이 주문당 CPU를 더 쓴다.
- **확정값:** P = 20(동기 비교군도 같은 값, P1에서 동기 P=20은 2,361건/s), insert core = max = 20, Q = W × 30초 × 0.8 = 70,632 → 70,000(최대 대기 약 24초), reviewStats core = max = P/2 = 10. `application.properties` 기본값·`.env.example`·서버 `.env`에 반영했다.
- **다음 단계에 주는 영향:** S 회차의 2W(약 5,900건/s)는 이 노트북의 유입 한계를 넘는다. S 회차는 W 배수 대신 트래픽 목표치(이벤트 10만·20만 명)로 다시 정의했다(2026-09-29, 위 "트래픽 목표치"·아래 "회차 구성").

**거절 응답 코드: 429 (2026-09-28 확정).** 대기 자리가 없을 때의 응답은 `omp.order.async.reject-status`(기본 429, 비교군 503)로 정한다. 둘 다 `Retry-After: 1`을 붙인다.
- **측정:** 채택 설정·4,000/s에서 거절 응답만 바꿔 4회 쟀다. 503은 3회 모두 붕괴(p95 약 1.2초, p99 약 4.8초, 연결 실패 5,488~7,502건), 429는 2회 모두 붕괴 없이 저하(W 2,914·2,894건/s, 접수 p95 246·372ms)였다. Tomcat이 503 응답 뒤 연결을 닫아 거절마다 새 TCP 연결이 생긴 것(초당 44 → 824~1,051개)이 원인이다. 연결 대기열(accept-count)을 1,000으로 늘려도 붕괴했다. 결과 README 7절.
- **표준 의미와 다른 선택이다.** 이 거절은 서버 쪽 대기 자리 부족이라 이름으로는 503("서버가 지금 처리할 수 없음")이 맞고, 429는 "이 클라이언트가 너무 많이 보냈다"는 뜻이다. 그래도 앱은 429로 둔다. 앱이 클라이언트 연결을 직접 받는 구성에서는 503이 부르는 재연결이 과부하 순간에 서버를 무너뜨렸고, 429는 같은 부하에서 버텼기 때문이다.
- **실서비스 구성(측정하지 않은 설계 판단):** 앞에 Nginx를 두면 클라이언트 연결은 Nginx가 받고, 앱과는 keepalive 연결 풀로 잇는다(`upstream { keepalive ... }`, `proxy_http_version 1.1`, `proxy_set_header Connection ""`). 앱은 429를 그대로 보내고, Nginx가 `proxy_intercept_errors on` + `error_page 429 =503 ...`으로 클라이언트에는 503 + Retry-After로 바꿔 보낸다. 이러면 클라이언트는 표준 의미의 503을 받고, 앱 쪽 연결은 끊기지 않는다(Nginx는 기본 동작에서 503 뒤에도 클라이언트 연결을 유지한다). 더 앞에서 `limit_req`로 사용자·IP별 초과 요청을 먼저 걸러 앱까지 오는 거절을 줄인다.
- **클라이언트:** Retry-After를 따르되 지터(무작위 지연)를 더해 재시도가 한순간에 몰리지 않게 한다. 재시도로 같은 주문이 두 번 저장되지 않게 하는 멱등 키는 개선 과제로 남긴다(현재 없음).
- **비교군 공정성:** 동기 경로에 빠른 실패를 넣어 비교할 때도 같은 429를 쓴다(응답 코드 차이가 방식 차이처럼 보이지 않게).

### 주문: 회차 구성 (B / A / S, C 보류)

설정은 위 "풀 크기 산정" 확정값으로 고정한다. 부하는 B·S가 위 목표치(절대값), A가 잰 용량 W의 배수다. 전부 open model이며 동기·비동기는 같은 서버에서 URL만 다르다.

| 회차 | 상황 | 부하 | 판정·기록 | 반복 |
|---|---|---|---|---|
| **B. 평상시** | 저녁 피크(중소 앱) | 초당 40건 고정 15분, `THRESHOLDS=strict` | 두 모드 모두 threshold 통과(오류 0, p95 < 200ms). 비동기 저장 완료 p95·p99(전체 구간). 확정 시간(접수 + 저장 완료)이 동기 응답과 비슷한지 = 비동기가 평소에 치르는 비용 | 모드별 3회 |
| **A. 용량** | 목표 대비 여유 | 0.6 / 0.8 / 1.0 / 1.2 / 1.4 × W, 계단마다 4분, `off` | 동기: 접수(=저장) p95 ≤ 200ms, 실패 0, dropped 0. 비동기: 접수 p95 ≤ 200ms, 거절 0, 실패 0, 계단 끝의 큐 길이 ≤ W×1초(큐가 늘지 않음), 저장 완료 p99 ≤ 30초. **"통과한 최대 단계 / 처음 실패한 단계"** 구간과 평상시 목표 대비 배수로 보고 | 1회 |
| **S-10만. 이벤트** | 쿠폰 오픈 직후, 참여자 10만 명 | `SCENARIO=spike`: 초당 40건 120초 → 5초 만에 1,707건 → 55초 유지 → 5초 만에 40건 → 300초 관찰 (이벤트 주문 = 1,667 × 60초 ≈ 10만 건), `off` | 아래 S 판정 | 동기·비동기 각 2회 |
| **S-20만. 이벤트** (헤드라인) | 참여자 20만 명 | 같은 형태, 스파이크 초당 3,373건 (이벤트 주문 ≈ 20만 건) | 아래 S 판정 | 동기·동기+빠른 거절·비동기 각 3회 |
| C. 지속 초과 | (보류) | — | 중소 앱 규모에서는 저녁 피크(초당 40건)가 용량을 넘지 않아 이 회차의 질문이 성립하지 않는다 | — |

**S 판정:** 비동기 — spike 구간 접수 p95 ≤ 200ms, 저장 완료 p99 ≤ 30초(판정 구간 = 스파이크 시작 ~ 끝+90초), 거절 수, 큐 최대·소진 시각, 거절 응답 p95. 동기 — spike 구간 성공 응답 p95·p99·max, 거절(429)·실패·타임아웃 수, dropped(k6가 보내지 못한 요청 = 받지 못한 주문), tomcat_busy. 세 방식 모두 "이벤트 주문 중 요구값(p95 ≤ 200ms) 안에 받은 건수"를 같은 기준으로 센다.

**동기 + 빠른 거절 (비교군, 2026-09-29).** "동기도 넘치는 요청을 빨리 거절하면 되지 않나"에 답하려고 넣는다. 동시에 처리하는 동기 주문 수에 상한(`omp.order.sync.max-in-flight`)을 두고, 넘으면 커넥션을 기다리지 않고 즉시 429 + Retry-After로 거절한다(`SyncOrderAdmission`, 비동기 대기 자리 예약과 같은 모양).
- **커넥션 대기 시간(Hikari `connection-timeout`)으로 하지 않는 이유:** 동기 요청은 Tomcat 요청 스레드(200개)를 먼저 잡고 들어오므로 커넥션을 기다리는 요청은 많아야 200 − P = 180개다. P=20 커넥션이 초당 약 2,360건을 처리하면 180번째도 약 76ms 뒤 커넥션을 얻어, Hikari 최솟값 250ms로도 시간 초과가 나지 않는다. 넘친 요청은 Tomcat 대기열에서 기다리며 지연만 늘어난다.
- **상한 120:** 동기 처리량 약 2,360건/s × 허용 대기 50ms(접수 p95 예산 200ms의 1/4) ≈ 118. Tomcat 스레드 200보다 작아야 거절이 실제로 일어나고, 남은 스레드가 거절 응답을 빠르게 돌려준다.
- 원래 동기(상한 없음)는 기본값 0으로 그대로 둔다.

**S 예측 (측정 전에 기록, 2026-09-29)**

| 이벤트 | 방식 | 예측 |
|---|---|---|
| 10만 명 (초당 1,707건) | 비동기 | W 아래라 큐가 거의 쌓이지 않음. 거절 0, 접수 p95 수십 ms 이하, 저장 완료 p99 1초 미만 |
| | 동기 | 동기 용량의 72%. p95 ≤ 200ms로 통과. **이 규모는 동기로도 충분하다** |
| 20만 명 (초당 3,373건) | 비동기 | 초과분(3,373 − W ≈ 420~750건/s) × 60초 ≈ 2.5만~4.5만 건이 큐(70,000칸)에 쌓여 거절 0. 저장 완료 최대 약 9~17초(한도 30초 안), 스파이크 뒤 약 10~17초에 소진. 위험: 유입이 노트북 유입 한계를 넘어(P2 6·8절) 접수 p95가 200ms를 넘을 수 있다 |
| | 동기 | 초과 약 1,000건/s가 Tomcat 대기열에 쌓인다. k6 VU 상한(4,000)에서 동시 요청이 막혀 지연 약 1.7초(4,000 ÷ 2,360), 나머지 약 6만 건은 보내지 못함(dropped = 받지 못한 주문). 커넥션 대기는 짧아 30초 타임아웃은 거의 없음 |
| | 동기 + 빠른 거절 | 동기 용량만큼(약 2,360건/s) 받고 초과분 약 30%(약 6만 건)는 즉시 429. 받은 주문의 p95 ≤ 200ms |

예측과 측정이 다르면 그 차이 자체가 분석 대상이다(스파이크 중 W 하락, 접수 경로와의 CPU 경쟁 등). B의 초당 40건은 두 모드가 모두 감당하는 부하라 strict 판정이 성립한다.

### 리뷰: 설계 판단 기준 (2026-09-24 확정)

리뷰 작성에서 지켜야 할 것을 우선순위로 정하고, 그 순서로 ②·③을 판단했다.

| 순위 | 대상 | 요구 수준 |
|---|---|---|
| 1 | 리뷰 원본 | 저장된 리뷰는 유실·중복 없이 남아야 한다 |
| 2 | 통계의 최종 정합성 | 개수·합계·평균이 최종적으로 원본과 정확히 일치해야 한다. 가게 평판·노출에 직결되며, 틀린 값이 지속되면 안 된다 |
| 3 | 통계 신선도 | 즉시 반영까지는 필요 없다 (수 초~수 분 지연 허용) |
| 4 | 작성 응답 지연 | 사용자가 불편하지 않은 수준이면 충분하다. 허용 한도 **성공 응답 p95 ≤ 500ms** (주문 접수의 200ms보다 완화) |

| 기준 | ② 같은 트랜잭션 | ③ 커밋 후 비동기 |
|---|---|---|
| 1. 원본 | 충족 | 충족 |
| 2. 최종 정합성 | 원자적으로 항상 일치 | 거절·실패·재시작 시 복구되지 않는 불일치 (재처리 없음) |
| 3. 신선도 | 즉시 반영 | 지연 반영 (허용 범위) |
| 4. 응답 지연 | 핫 가게 stats 행 락 대기 포함 → 한도 안인지 검증 | 더 짧음 |

**결정: ② 채택.** ③이 앞서는 것은 4순위 항목뿐이고 2순위에서 뒤진다. ③에 남는 근거인 "통계 장애가 리뷰 작성을 막으면 안 된다"는 복구 경로가 있어야 성립하며, ②에서 통계만 실패하는 경우는 통계 행 누락 같은 데이터 결함이나 극단적 락 대기 타임아웃뿐이다.

**채택 검증 (측정 전 고정):** 리뷰 본측정(`MODEL=closed`, 파일럿 확정 VUS·SHOP_POOL, 15분 × 3회)의 ② **모든 회차**에서
- 데드락 증가분 0, `verify.sql` 2)·2-b)·2-c) 0행, 5xx·기타 실패 0
- 성공 응답 p95 ≤ 500ms

이 조건을 만족하면 ②를 확정한다. closed model의 지연은 VU 수에 비례해 커지므로, 이 판정은 "가게당 동시 작성 수십 건"이라는 과장된 경합에서도 한도 안인지 확인하는 여유 검증이다. p95만 한도를 넘으면 같은 VUS에서 `SHOP_POOL=1000`(분산) 회차를 3회 추가한다. 분산에서 한도 안이면 ②를 유지하고 "핫 가게 집중 시 지연"을 한계로 기록한다. 분산에서도 넘으면 결정을 다시 연다(③ + 복구 경로 구현 검토).

**③의 역할:** 비교군. "비동기로 바꾸면 응답 지연은 X→Y로 줄지만, 반영 지연과 거절·실패 N건이 생긴다"를 기록한다. ③의 결과는 채택 여부를 가르지 않는다. closed model에서는 ③의 응답이 빨라 유입이 더 커지므로, 같은 유입률에서의 대가를 보이려면 3절 끝의 open model 회차를 추가한다.

**③으로 전환할 조건:** 통계 장애와 무관하게 리뷰 작성이 성공해야 한다는 요구가 생기거나, 랭킹·검색 색인·알림처럼 리뷰에 딸린 후속 작업이 늘어나면 Outbox 기반 비동기와 재처리를 함께 도입한다.

### 리뷰: 측정 전 확정할 항목

**원인 재현은 부하 측정과 별도로 한다 (2026-09-26 완료).** `ReviewLockReproductionTest`가 커넥션 2개로 두 트랜잭션의 실행 순서를 한 단계씩 통제하고, **같은 순서를 ①과 ②에 적용한다.** ①은 bench/review-before가 실행하는 SQL(가게 조회 → 리뷰 INSERT → 계산한 절대값으로 shops UPDATE), ②는 main sync 모드의 SQL(리뷰 INSERT → 원자 증가 UPDATE)이다. ① 스키마는 테스트 동안만 shops에 통계 컬럼을 추가한다. `OMP_TEST`에서 돌고, 5회 반복해 모두 같은 결과였다.

| 검증 | 제어한 실행 순서 | ① 결과 | ② 결과 |
|---|---|---|---|
| (a) 데드락 | T1·T2가 모두 리뷰 INSERT(외래 키 검사로 shops 행 S락)를 마친 뒤 T1, T2 순으로 통계 UPDATE | 한쪽이 1213(데드락)으로 롤백, `lock_deadlocks` +1, 희생자의 리뷰도 롤백 | T2가 T1의 stats 행 락을 기다렸다가 둘 다 커밋, `lock_deadlocks` +0, 통계 2개·합계 9·평균 4.50 |
| (b) 갱신 유실 | 리뷰 10개·합계 30에서 T1·T2가 같은 이전 값을 읽고, T1(5점) 커밋 뒤 T2(4점) 커밋 | 리뷰는 12개·39점인데 통계는 11개·34점 (갱신 1건 유실, 데드락 없이 발생) | 통계 12개·39점·평균 3.25 |
| ②·③ 통계 실패 정책 | 통계 행 누락을 강제해 같은 리뷰 요청 실행. `ReviewStatsMissingRowSyncModeTest`·`ReviewStatsMissingRowAsyncModeTest` | — | ②는 리뷰도 롤백, ③은 리뷰 유지 + 통계 실패 기록. 자동 복구를 검증하는 테스트는 아님 |

락 대기는 시간에 기대지 않고 `performance_schema.data_lock_waits`에 대기가 보일 때 다음 단계로 넘어간다. (`information_schema.INNODB_TRX`는 0.1초 넘게 읽히지 않아야 새로 채워지는 캐시라 자주 폴링하면 대기 전 상태만 보인다.)

통계 실패를 의도적으로 주입하는 정책 검증은 정상 부하 회차와 별도로 기록한다. 실패 경계를 검증하는 사례의 실패 건수를 정상 부하의 무오류 목표와 섞지 않는다.

| 설정 | 파일럿·본측정 기준 |
|---|---|
| 장비 | 데스크탑 k6, 별도 노트북 Spring Boot + MySQL, 두 장비 모두 유선 LAN |
| 파일럿 | `MODEL=closed`, `VUS=50`, `SHOP_POOL=3`, 1분에서 시작. 최적값이나 실서비스 트래픽으로 주장하지 않음 |
| 본측정 | 파일럿에서 확정한 VUS·SHOP_POOL, 동일한 요청 크기·평점 1~5 분포, 15분 × 설계별 3회 |
| JVM·DB | JDK 21, `-Xms2g -Xmx2g`, HikariCP P (위 "풀 크기 산정" 확정값). 실제 MySQL 버전·격리 수준·주요 DB 설정 기록 |
| ③ 통계 풀 | core = max = P/2, queue 2000, AbortPolicy ("풀 크기 산정" 규칙). 그 이상 튜닝하지 않음 |
| 정상 부하 판정 | ②는 위 "채택 검증" 조건(모든 회차 데드락·불일치·실패 0, 성공 p95 ≤ 500ms). ③은 비교군이라 거절·실패·불일치를 회차별 건수로 기록하되 합격 기준으로 쓰지 않음. HTTP threshold 통과만으로 성공 처리하지 않음 |

③을 해석할 때: ③도 요청과 워커가 같은 HikariCP·DB를 쓰므로 자원 경쟁은 남는다. 워커가 핫 행 락을 기다리는 동안에도 커넥션을 쥔다. ③은 거절·실패를 기록할 뿐 재처리·재집계가 없어 누락이 지속된다. 따라서 ③의 정상 부하 무오류 결과를 장애 후 최종 정합성 보장으로 쓰지 않는다.

### 캐시: 가게 목록 만료 시간 5분 (2026-09-26 확정)

기존 값은 24시간 + 0~3초 Jitter였고 근거가 없었다. 영업 상태·가게 정보·새 가게는 변경 시 캐시를 무효화하므로, 만료 시간이 책임지는 값은 **평점·리뷰 수**뿐이다. 그 허용 지연을 아래 순서로 정했다. 상세 근거와 계산표는 [PORTFOLIO-1-3-draft.md](PORTFOLIO-1-3-draft.md) "만료 시간 5분의 근거".

| 기준 | 판단 |
|---|---|
| 요구 | 1-1에서 통계 신선도는 3순위, "수 분" 지연 허용. 가게 상세는 캐시하지 않아 항상 최신 |
| 길 때의 비용 | 평점 지연. 리뷰 한 건은 평균을 최대 4/(n+1) 움직여(리뷰 100개면 0.04) 체감이 작다 |
| 짧을 때의 비용 | 재조회 부하(인스턴스당 ≤ 키 수 ÷ 만료 시간). 테스트 규모(키 52개)에서는 어느 값이든 무시할 수준이고, 서비스 규모(배민 전국 약 30만 개 기준, 인기 키 약 1.4만 개 가정)에서는 60초 초당 약 226회 vs 5분 약 45회로 5배 차이 |
| 결정 | "수 분" 안에서 서비스 규모 부하를 60초 대비 1/5로 줄이는 **5분**. Jitter는 만료 시간의 0~10%(0~30초) |
| 다시 볼 조건 | 가게·필터 조합이 늘어 키 수가 커지거나, 평점 반영 지연에 대한 요구가 생기면 같은 식으로 재계산 |
| 구현 (2026-09-26) | `CacheType.SHOPS` 5분, `JitteredExpiry`(0~10%), `ShopService` 생성·수정 시 `@CacheEvict(allEntries)`(트랜잭션 인식 캐시라 커밋 후), `@Cacheable(sync = true)`, 목록 쿼리 `ORDER BY shop_id DESC`. 검증: `ShopListCacheTest`, `JitteredExpiryTest` |

테스트 DB의 가게 1000개는 위치 필터가 없는 목록 API 특성상 "배달 권역 하나"(전국 30만 ÷ 기초자치단체 226 ≈ 평균 1,300개, 추정)에 해당한다. 1-1·1-2 결과는 가게 수의 영향을 거의 받지 않는다(리뷰는 가게 3개 집중, 주문은 PK 조회·외래 키 없음).

## 3. 실행 순서

### 사전 준비 (1회)
1. 노트북: 전원 연결 + "최고 성능" 전원 계획, 방화벽 8080 인바운드 허용, `ipconfig`로 IP 확인. **Windows 업데이트는 설치를 끝낸 뒤(재부팅 포함) 측정 기간 동안 일시 중지**한다. 업데이트 설치(Windows Modules Installer Worker, TiWorker.exe)는 CPU를 대부분 잡아먹는다. **데스크톱(부하 발생기)도** 측정 중에는 게임·영상 등 무거운 프로그램을 끈다. k6가 CPU를 못 받으면 목표 부하를 못 내고, 그 결과가 서버 한계처럼 보인다.
2. MySQL: `innodb_print_all_deadlocks`는 **OFF**로 둔다. ① 파일럿과 결정적 재현 테스트 때만 `SET GLOBAL innodb_print_all_deadlocks = ON`으로 켜서 락 정보를 캡처하고, 15분 본측정 전에 다시 끈다. ①은 요청 대부분이 데드락에 걸릴 수 있어 전건 로그가 ①에만 부하를 더해 ①②③ 성능 비교를 오염시키기 때문이다. 본측정의 데드락 수는 `verify.sql` 1)의 `lock_deadlocks` 증가분으로 센다(`status`가 `enabled`인지 확인).
3. 새 스키마(OMP)로 main 서버 1회 기동 → 테이블 생성 확인 → `sql/seed.sql`.
4. 스모크: RATE 10, 30초로 01·02 실행 → check 실패 0. **대역폭 확인**: 요약의 (`data_sent` + `data_received`) ÷ `iterations` = 요청당 바이트. 이 값 × 스파이크 유입률(2W) × 1.3(TCP/IP 오버헤드) × 8이 링크 속도의 70% 미만이어야 한다(100 Mbps 링크면 70 Mbps). 두 장비의 링크 속도(`Get-NetAdapter | Select-Object Name, InterfaceDescription, LinkSpeed`)를 환경 표에 기록한다.
5. 결과 폴더 `benchmark/results/`가 있는지 확인. k6는 폴더를 만들지 않으므로 `OUT_DIR`는 존재하는 경로여야 한다.
6. 2절 "풀 크기 산정" 파일럿(P1·P2)으로 P·k·W·Q를 확정하고 `.env`에 반영한다(재시작만). `.env.example`·`application.properties` 기본값도 같은 값으로 맞춰 커밋한다. 이후 모든 회차는 이 값으로 고정하고, 회차마다 0절 확인 명령으로 적용값을 기록한다.

### 매 회차 공통 절차 (순서가 결과를 좌우한다)
1. 서버 재시작 (해당 브랜치·기동 옵션). ①을 처음 기동해 shops 통계 컬럼이 NULL이면 워밍업 전에 0으로 초기화한다. 본측정 초기화는 4번에서 다시 한다.
2. **워밍업**: 같은 스크립트로 1~2분 실행하고 결과는 버린다. 리뷰는 `MODEL=open`, 낮은 `RATE`를 명시하거나 `MODEL=closed`에서 `VUS`를 낮춘다. closed 모드에서 RATE만 바꿔도 부하는 줄지 않는다. ①의 오류는 원인별로 확인한다.
3. 워밍업 작업 **소진 확인**: 4절 폴링에서 `queued=0` **그리고** `active=0`.
4. `sql/reset-round.sql` 실행 (워밍업 쓰기 제거). ① 회차는 하단 `UPDATE shops ...`도 실행.
5. **시작 전 값 기록**: `verify.sql` 1)·1-b) (lock_deadlocks, row lock) + 카운터 3종 (`omp.order.async.rejected`, `omp.review.stats.rejected`, `omp.review.stats.failed`). 모두 기동 후 누적값이라 **증가분**으로 쓴다.
6. 4절 폴링 시작 → 본측정 실행.
7. 종료 후 **완료 대기**: `queued=0`·`active=0`이 되고 `COUNT(*)`가 더 변하지 않을 때까지. 제한 5분 초과 시 "미완료"로 기록한다.
8. 해당 설계의 검증 SQL 실행: ①은 1)·1-b)와 주석 해제한 4-a)·4-b), ②·③은 1)·1-b)·2)·2-b)·2-c). 리뷰는 `SELECT COUNT(*) FROM reviews`도 기록한다. 주문은 3)을 사용한다. 서버 로그와 k6 JSON·CSV·폴링 CSV를 `benchmark/results/<날짜>-<TAG>-r<N>/`로 이동.
9. 같은 조건 **3회** → 성능은 중앙값과 범위, 오류·불일치는 **모든 회차의 건수**를 기록. 회차 사이 **15분 휴식**(직전 부하 종료 기준)하고, 회차 전후로 충전기 연결(`Win32_Battery.BatteryStatus` = 2)과 노트북 클럭(`\Processor Information(_Total)\% Processor Performance`, 5초 간격)을 기록한다. 이 조건을 통제하지 않은 회차는 같은 설정에서 처리량이 통제 회차 평균 대비 −13%~+19%로 흔들렸다(7절). 가능하면 리뷰 실행 순서를 ①②③ / ②③① / ③①②로 순환해 특정 설계가 항상 마지막에 측정되는 것을 피한다.
10. 주문 A(용량 계단)는 계단마다 재시작하지 않고 `reset-round.sql`과 잔여 작업 0 확인만 한다(명령의 read 프롬프트). 주문 B·S·C와 리뷰 ①②③은 1~9 전부. 주문 B·S도 모드 순서를 회차마다 번갈아 둔다.

### 본측정 명령 (데스크탑, 리포 루트에서)
```bash
S=http://<서버IP>:8080; OUT=benchmark/results

# ── 주문: 회차 B·A·S·C (2절 "주문: 회차 구성"). 설정은 풀 크기 산정 확정값, 4절 폴링은 측정 내내 켜 둔다.
#    B·S·C는 run마다 공통 절차 1~5(재시작 → 워밍업 → 소진 확인 → reset → 시작 전 값)를 수행하고, 모드 순서를 회차마다 번갈아 둔다.
#    k6가 출력하는 "[run] 시작/종료" 시각(스파이크는 구간 시각)을 결과 문서에 옮긴다. 저장 완료 분포는 그 시각으로 hist_window.py 계산.
W=<파일럿 P2의 W>

# B. 평상시 — 초당 40건(트래픽 목표치) 15분, strict. r1: sync→async, r2: async→sync, r3: sync→async
B=40
k6 run -e BASE_URL=$S -e RATE=$B -e DURATION=15m -e MODE=sync  -e TAG=B-r1 -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
#   (공통 절차 1~5)
k6 run -e BASE_URL=$S -e RATE=$B -e DURATION=15m -e MODE=async -e TAG=B-r1 -e OUT_DIR=$OUT benchmark/k6/01-order-api.js

# A. 용량 계단 — 계단마다 두 모드를 번갈아 4분씩. 재시작은 생략하고 run마다 노트북에서 reset-round.sql 만 실행한다.
for X in 0.6 0.8 1.0 1.2 1.4; do
  R=$(awk "BEGIN{printf \"%d\", $W*$X}")
  for M in sync async; do
    read -p "[A x$X $M] 노트북에서 reset-round.sql 실행, queued=0·active=0 확인 후 Enter: "
    k6 run -e BASE_URL=$S -e RATE=$R -e DURATION=4m -e MODE=$M -e THRESHOLDS=off -e TAG=A-x$X -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
  done
done

# S. 이벤트 스파이크 — 평상시 40 → 스파이크(10만 명 1,707 / 20만 명 3,373) → 평상시. 5초 상승 + 55초 유지 + 5초 하강 = 이벤트 주문 약 10만·20만 건. 총 약 8분.
#    방식: async / sync / syncff(= sync + 서버 기동 인자 --omp.order.sync.max-in-flight=120). 20만은 세 방식 각 3회, 10만은 async·sync 각 2회.
#    방식과 이벤트 규모의 순서를 회차마다 섞는다(시간대 효과가 방식 효과처럼 보이지 않게). run마다 공통 절차 1~5.
SPIKE=3373   # 또는 1707
k6 run -e BASE_URL=$S -e SCENARIO=spike -e BASE_RATE=40 -e SPIKE_RATE=$SPIKE -e SPIKE_HOLD_S=55 -e MODE=async -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=S20-r1 -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
#   (공통 절차 1~5)
k6 run -e BASE_URL=$S -e SCENARIO=spike -e BASE_RATE=40 -e SPIKE_RATE=$SPIKE -e SPIKE_HOLD_S=55 -e MODE=sync  -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=S20-r1 -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
#   저장 완료 판정(비동기): k6가 출력한 "저장 완료 판정 구간"을 그대로 넣는다.
#   python benchmark/tools/hist_window.py server-hist.txt --metric omp_order_async_completion_seconds --label outcome=completed --from <spike 시작> --to <spike 끝+90초> --slo 30
#   큐 최대·소진 시각은 server-metrics.csv 의 insert_queued 시계열에서 읽는다.

# C. 지속 초과 — 보류(2절 "회차 구성": 중소 앱 목표치에서는 저녁 피크가 용량을 넘지 않는다).

# 리뷰: 각 명령 전에 해당 서버 브랜치·모드로 공통 절차 1~5를 수행한다. 세 명령을 같은 서버 모드에서 연속 실행하지 않는다.
#   TAG는 결과 파일 이름일 뿐 서버 모드를 바꾸지 않는다. VUS·SHOP_POOL은 파일럿 확정값으로 세 설계에 동일 적용.
#   아래는 r1 예시. r2·r3도 각각 초기화 후 실행한다. 성공 응답 지연은 reviews_success_duration 으로 요약에 나오므로 CSV는 필요 없다.
# ① bench/review-before
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=before-r1 -e OUT_DIR=$OUT -e THRESHOLDS=off benchmark/k6/02-review-api.js
# ② main, 기본 기동 (채택. 2절 채택 검증 조건으로 판정)
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=sync-r1 -e OUT_DIR=$OUT benchmark/k6/02-review-api.js
# ③ main, .env 에 OMP_REVIEW_STATS_MODE=async (비교군)
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=async-r1 -e OUT_DIR=$OUT benchmark/k6/02-review-api.js
# ②의 p95만 한도를 넘으면 2절대로 같은 VUS에서 -e SHOP_POOL=1000 회차를 3회 추가한다.
# ③의 대가를 같은 유입률에서 보이려면(선택) ②·③에 MODEL=closed·VUS 대신 MODEL=open과 같은 RATE를 쓴다.
#   RATE는 ②가 감당하는 값(② closed 성공 처리량 이하), SHOP_POOL·DURATION 동일, dropped_iterations=0 확인.
```

### 리뷰 ① (개선 전, `bench/review-before`) 회차
- `git checkout bench/review-before` → `./gradlew bootJar` → 기동. 첫 기동에서 ddl-auto=update가 `shops`에 `review_count`, `rating_sum`, `average_rating`을 추가한다.
- 기존 행의 DECIMAL 컬럼은 NULL이라 `reset-round.sql` 하단 `UPDATE shops SET ... = 0`을 주석 해제해 실행해야 갱신 시 NPE가 나지 않는다.
- **파일럿 1분 먼저.** `-e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=1m -e THRESHOLDS=off -e TAG=before-pilot`에서 시작한다. 파일럿 동안만 `innodb_print_all_deadlocks = ON`으로 켜서 데드락 로그로 의도한 원인(FK S락 → 같은 행 X락)인지 확인하고, 끝나면 끈다. 부하기 여유·서버 자원 사용을 기록한다. 재현이 안 되면 FK·브랜치·초기화와 제어된 재현 테스트부터 확인한 뒤 SHOP_POOL·VUS를 조정한다. 특정 건수의 오류가 나올 때까지 부하를 올리는 것을 목표로 삼지 않는다. 확정 조건은 ①~③에 동일 적용한다.
- closed model 에서는 ①의 데드락 롤백(500)이 빠르게 끝나 처리량이 오히려 높게 보일 수 있다. 처리량은 반드시 `reviews_created`(2xx) 기준으로 비교하고 `iterations` 를 쓰지 않는다.
- **판정은 threshold가 아니다.** `http_req_failed`가 1% 미만이어도 데드락은 발생한다. `lock_deadlocks` 증가분과 락 로그를 근거로 삼고, `reviews_5xx`에는 다른 원인이 섞일 수 있으므로 따로 대조한다. `verify.sql` 4-a)로 저장된 리뷰와 shops 통계의 차이를 확인한다.
- 이 회차는 `shop_review_stats`를 갱신하지 않으므로 2)·2-b)·2-c)를 정합성 근거로 쓰지 않는다. 4-a)와 shops의 평균을 별도로 확인한다.
- 이 브랜치에서 main의 정합성 테스트(`ReviewStats*Test`)는 실패한다. 벤치마크 전용 브랜치다. main과 같은 테스트 DB 가드(`OMP_TEST` 확인)가 있어 테스트를 돌려도 OMP는 지워지지 않지만, 서버 노트북에서는 `bootJar`만 쓴다.

## 4. 서버 측 지표 폴링 (k6가 못 재는 것)

5초 간격으로 두 파일을 남긴다. (Git Bash, 종료는 Ctrl+C)
- `server-metrics.csv`: 두 풀의 queued·active, 거절·실패 카운터, Hikari 대기, Tomcat 바쁜 스레드 (시계열)
- `server-hist.txt`: 지연 Timer의 누적 히스토그램 버킷 줄. 누적값이라 두 시점 차이로 임의 구간의 분포를 계산한다 (`benchmark/tools/hist_window.py`)

```bash
S=http://<서버IP>:8080
m() { curl -s "$S/actuator/metrics/$1" | grep -o '"value":[0-9.E+-]*' | head -1 | cut -d: -f2; }
echo "time,insert_queued,insert_active,stats_queued,stats_active,order_rejected,order_failed,stats_rejected,stats_failed,hikari_active,hikari_pending,tomcat_busy,process_cpu,system_cpu" > server-metrics.csv
: > server-hist.txt
while true; do
  T=$(date +%T)
  echo "$T,$(m 'executor.queued?tag=name:insertTaskExecutor'),$(m 'executor.active?tag=name:insertTaskExecutor'),$(m 'executor.queued?tag=name:reviewStatsExecutor'),$(m 'executor.active?tag=name:reviewStatsExecutor'),$(m omp.order.async.rejected),$(m omp.order.async.failed),$(m omp.review.stats.rejected),$(m omp.review.stats.failed),$(m hikaricp.connections.active),$(m hikaricp.connections.pending),$(m tomcat.threads.busy),$(m process.cpu.usage),$(m system.cpu.usage)" | tee -a server-metrics.csv
  curl -s "$S/actuator/prometheus" | grep -E '^omp_(order_async_(completion|queue_wait)|review_stats_lag)_seconds_(bucket|count)' | sed "s/^/$T /" >> server-hist.txt
  sleep 5
done
```

- **서버 지연 Timer** (모두 버킷 5ms~120s, 20~30초 구간은 5초 간격)
  - `omp.order.async.completion{outcome=completed|failed}`: 비동기 주문의 제출(검증 통과 후)부터 저장 커밋까지. 사용자가 체감하는 확정 시간 ≈ k6 `order_accepted_duration` + 이 값
  - `omp.order.async.queue.wait`: 제출부터 워커가 작업을 시작할 때까지의 큐 대기. completion − queue.wait ≈ INSERT 시간
  - `omp.review.stats.lag`: 리뷰 커밋부터 통계 커밋까지 (③ 비교군에서만 기록)
- **구간 분포 계산**: 예) 스파이크 구간의 저장 완료 분포와 30초 이내 비율.
  `python benchmark/tools/hist_window.py server-hist.txt --metric omp_order_async_completion_seconds --label outcome=completed --from <시작> --to <끝> --slo 30`
  구간은 폴링 간격(5초)만큼 넓어질 수 있다. 백분위수는 버킷 안 보간 추정이고, `--slo 30`의 "30초 이하 비율"은 버킷 경계라 정확한 값이다(p99 ≤ 30초 ⇔ 이 비율 ≥ 99%). 서버를 재시작하면 누적값이 초기화되므로 같은 기동 안의 구간만 계산한다.
- **잔여 작업 종료 관측 시점** = k6 종료 후 해당 풀의 `queued=0`이고 `active=0`이 처음 관측된 시각. 이후 원본 행 수와 통계가 안정됐는지 확인한다. 5초에 조회 비용이 더해지는 폴링이므로 실제 샘플 간격과 함께 기록한다. 개별 작업의 지연은 위 Timer로 본다.
- 큐 용량만으로 큐가 비는 시간을 단정하지 않는다. 리뷰는 작업이 끝난 뒤에도 거절·실패로 통계가 누락될 수 있으므로 최종 SQL 검증을 함께 한다.
- `hikari_pending`이 **0보다 큰 상태**로 관측되면 커넥션 획득 대기가 있다는 뜻이다. 지속 시간과 응답 지연을 함께 기록한다. 5초 표본의 최댓값은 순간 최대치를 보장하지 않는다.
- `tomcat_busy`가 200(기본 최대)에 붙으면 요청 스레드가 소진된 상태다. 동기 방식의 붕괴 양상을 설명하는 근거로 쓴다.
- `process_cpu`는 서버 JVM, `system_cpu`는 노트북 전체 CPU 사용률(0~1)이다. 차이가 대부분 MySQL 몫이며, 부하가 없는데 `system_cpu`가 높으면 다른 프로세스(Windows 업데이트 설치·백신 검사 등)가 CPU를 쓰는 것이므로 측정을 멈춘다. 회차 시작 전 10초 평균이 15% 미만인지 확인한다(2026-09-26 실제로 시스템 75~95% 상태에서 `/ping` p99가 35ms → 해소 후 5ms).
- 부하 발생기 CPU도 함께 기록한다. 회차 시작 전 5초 평균 30% 미만, 측정 중 평균 80% 미만이면서 80% 이상 표본이 2번(10초) 연속 나오지 않아야 k6 결과를 서버 성능으로 읽을 수 있다. k6 요약의 `iteration_duration` − `http_req_duration` 평균(정상 0.2~0.3ms)도 함께 본다. 데스크톱 PowerShell에서 측정과 동시에 실행:
  `Get-Counter "\Processor(_Total)\% Processor Time" -SampleInterval 5 -MaxSamples 42 | % { "{0:HH:mm:ss},{1:N1}" -f $_.Timestamp, $_.CounterSamples[0].CookedValue } > client-cpu.csv`
  (2026-09-26 파일럿 P1 P=10: 데스크톱에서 게임이 CPU 47%를 쓰는 동안 측정 → 데스크톱 전체 83~97%, 처리량이 P=5보다 낮게 나오고 k6 전송·수신 최대 약 1초, 서버는 중간중간 요청이 0인 채 놀았다. 무효 처리하고 재측정)

## 5. 결과 읽는 법

| 지표 | 의미 |
|---|---|
| `iterations` | **시도** 수. 실패·503 포함. 접수량이 아니다 |
| `orders_accepted` / `reviews_created` | 주문은 접수 응답 수(202+Location), 리뷰는 저장 성공 응답 수(2xx). ③의 리뷰 성공 응답은 통계 반영 완료를 뜻하지 않음 |
| 리뷰 15분 처리량 | `reviews_created` ÷ 15분 (closed model). ①②③ 같은 VUS 에서 비교 |
| `orders_rejected` | 거절 응답(429, 비교군 503) 수. 서버 `omp.order.async.rejected` **증가분**과 같아야 한다 |
| `orders_failed_other` / `reviews_5xx` | 그 외 실패 / 리뷰 5xx. 데드락 외 원인을 포함할 수 있으므로 로그와 대조 |
| `completed_orders` (verify 3) | 커밋 완료량. **완료 시점 이후** 값. `orders_accepted` = `completed_orders` + `omp.order.async.failed` 증가분이어야 한다 |
| `http_req_duration` p95/p99 | 응답 종류가 섞인 전체 지연. 판정에는 아래 Trend를 쓴다 |
| `order_accepted_duration` | 주문 성공 응답만의 지연. 동기는 저장 완료(200)까지, 비동기는 접수(202)까지. **접수 p95 ≤ 200ms 판정** |
| `order_rejected_duration` | 거절 응답의 지연. 거절도 빨라야 백프레셔가 성립한다 (거절은 DB에 가기 전, 대기 자리 예약에서 일어난다) |
| `reviews_success_duration` | 리뷰 2xx 응답만의 지연. **② 채택 검증(p95 ≤ 500ms)** |
| `omp.order.async.completion` 등 서버 Timer | 4절 참고. 비동기 저장 완료 p99 ≤ 30초 판정은 `hist_window.py --slo 30` |
| `dropped_iterations` | **0**이어야 "초당 N건 유입 유지" 주장 성립. 쌓이면 `MAX_VUS` 부족 또는 서버 포화 |
| `checks` | strict 회차에서 100% |

해석 노트:
- 바닥값은 참고선이다. **"API p95 − ping p95 = 서버 처리 시간"** 같은 뺄셈은 하지 않는다 (서로 다른 분포의 백분위수는 뺄 수 없다). 서버 내부 구간 시간은 별도 계측이 필요하다.
- 비동기 주문에서 거절(429)이 나오면 insert 큐(Q = 30초분)가 가득 찬 것이다 → 접수 거절(백프레셔). 큐가 크므로 처리량 W를 조금 넘는 유입에서는 거절이 몇 분 뒤에야 나온다. 그래서 지속 용량은 "거절이 없었다"가 아니라 **"큐 길이가 늘지 않았다"**(A 회차 판정)로 본다. 스파이크(S)에서는 큐가 넘친 분량을 흡수하고 넘는 분량만 거절된다. dropped_iterations·접수 p95·다른 오류·저장 완료 분포를 함께 확인한다. (CallerRunsPolicy는 제거됨. 포화 시 톰캣 스레드가 몰래 INSERT하는 구간은 없다.)
- 유실 판정: `orders_accepted == completed_orders`(총건수)는 기본 점검이다. 누락과 중복이 상쇄될 수 있으므로 요청별 대조는 3단계 과제.
- 리뷰 ②·③: `verify.sql` 2)·2-b)·2-c)가 모두 0행이어야 최종 집계가 일치한다. rejected·failed는 ③의 비동기 카운터이며, ②의 실패는 HTTP·롤백·로그로 확인한다. ②는 이 조건이 채택 검증의 일부이고, ③(비교군)은 두 카운터 증가분과 불일치를 건수로 기록한다.
- **불일치 가게 수와 누락 갱신 수는 다르다.** 한 가게에 100건이 누락되면 2)의 결과는 1행이다. 개수 부족분은 2)의 가게별 `max(actual_count-review_count, 0)` 합계에 2-b)의 `reviews_without_stats_row` 합계를 더한다. 초과분은 2)의 `max(review_count-actual_count, 0)`을 별도로 합산한다. 부족분·초과분·불일치 가게 수·거절/실패 증가분을 따로 기록한다. 종료 후 남은 불일치는 **지속된 미반영**이며 단순 지연으로 설명하지 않는다.
- 리뷰 ①: 데드락 증가분·락 로그, 4-a)의 불일치 가게 수와 개수 부족/초과분을 별도로 기록한다. ①→②는 모델 분리와 원자 갱신의 결합 효과다.
- 리뷰 지연: k6 요약의 `http_req_duration`은 실패 응답까지 포함한다. 성공 응답 p95·p99는 `reviews_success_duration`으로 요약에 바로 나온다. 성공 표본이 없으면 0ms 대신 "측정 불가"로 기록한다.
- 리뷰 저장 수: `reviews_created`와 `SELECT COUNT(*) FROM reviews`를 함께 기록한다. 응답 유실·타임아웃이 있으면 서버 커밋 수와 클라이언트 성공 응답 수가 달라질 수 있으며, 총건수 일치는 요청별 대조를 대신하지 않는다.
- 설계 해석: ①→②는 결함 제거의 근거, ② 단독 결과는 채택 검증(2절), ②→③은 비교군 기록이다. ③의 p95가 더 낮아도 채택 근거가 되지 않는다(2절 우선순위상 4순위 항목). closed model의 지연 차이는 같은 VU 수에서의 결과이며 같은 유입률의 비교가 아니다.
- 주문 측정은 POST 응답만 본다. SSE 연결·폴링을 포함한 전체 사용자 흐름의 성능은 아니다. 결과 표에 범위를 명시한다.

## 6. 매 측정 기록 환경 표 (결과 문서에 복사)

```
| 항목 | 값 |
|---|---|
| 측정일시 / 회차 / TAG | 2026-XX-XX / N회차 (3회 중) / 예: S-r1 (주문 B·A·S·C, 리뷰 before·sync·async) |
| 커밋 SHA / 브랜치 | main <sha> 또는 bench/review-before <sha> |
| 서버 | 노트북 모델, CPU, RAM, 전원 연결+최고 성능 모드, 클럭(HWiNFO) |
| 부하기 | 데스크탑 CPU, RAM, OS, k6 버전, 측정 중 CPU·네트워크 사용 |
| 네트워크 | 서버·부하기 모두 유선 LAN, 링크 속도·네트워크 경로 |
| JVM | 21.x, -Xms2g -Xmx2g, GC 종류 |
| MySQL | 실제 버전, transaction_isolation(리뷰 경로의 실제 적용값 확인), innodb_buffer_pool_size, innodb_flush_log_at_trx_commit, 앱과 동거 |
| 커넥션 풀 | HikariCP P = <파일럿 확정값> (단일 풀, 조회·INSERT 공유). 파일럿 P1 처리량 표 첨부 |
| 스레드 풀 | omp.executor.* (insert core = max = k, queue = Q / reviewStats core = max = P/2, q2000 / sse 10/30/q200), insert·reviewStats 거절 정책 Abort, sse CallerRuns. 파일럿 P2 W 표 첨부 |
| 리뷰 통계 모드 | omp.review.stats.mode = sync(기본·채택) / async(비교군) (① 브랜치는 해당 없음) |
| 카운터 (시작 전 → 종료 후) | omp.order.async.rejected, omp.review.stats.rejected, omp.review.stats.failed |
| 데드락 카운터 (시작 전 → 종료 후) | lock_deadlocks N → M (차이 = 발생 건수), Innodb_row_lock_waits/time |
| 데이터 | seed.sql (users 10만, shops 1천, carts 10만), 회차마다 reset-round.sql (워밍업 후) |
| 부하 | k6 버전, SCENARIO/MODEL, RATE 또는 VUS, DURATION, SHOP_POOL, MAX_VUS, THRESHOLDS, dropped_iterations |
| 바닥값 | /ping p95 = X ms (참고선) |
| 잔여 작업 종료 관측 | k6 종료 후 queued=0·active=0 관측까지 N초, 실제 폴링 간격·최종 SQL 검증 결과 |
```

## 7. 흔한 함정

- **절차 순서**: reset을 워밍업 앞에 두면 워밍업 쓰기가 남아 완료량·접수량 비교가 깨진다. 3절 순서대로.
- **카운터는 누적값**: actuator 카운터와 lock_deadlocks는 기동 후 누적. 반드시 시작 전 값을 기록하고 증가분을 쓴다.
- **부하기 모니터링**: k6 실행 중 데스크탑 CPU가 평균 80% 이상이거나 80% 이상이 2표본(10초) 연속이면 부하기 병목 → 결과 무효(4절). 2026-09-26 데스크톱에서 게임을 켠 채 잰 회차는 처리량이 P=5보다 낮게 나왔다.
- **노트북 전원·상태**: 충전기를 뺀 회차(확인된 것은 20:07 P=15 2,163/s)와 뺀 것으로 보이는 18:45~19:20 회차는 같은 설정에서 통제 회차 평균보다 5~13% 낮았다. 반대로 휴식·기록 없이 잰 일부 회차(재부팅 11분 뒤 P=20 2,810/s 등)는 10~19% 높았는데, 클럭 기록이 없어 원인을 분리하지 못했다. 그래서 회차마다 15분 휴식·충전기 확인·클럭 기록(3절 9번)을 하고, 이 조건을 지킨 회차만 결과로 쓴다. 발열이 걱정되면 충전기를 빼지 말고 제조사 앱의 충전 한도(80%)와 통풍으로 대응한다.
- **503마다 연결이 끊긴다(부하 발생기 포트 고갈)**: Tomcat은 503·400·500 응답 뒤 연결을 닫는다. 거절이 많으면 k6가 새 연결을 계속 열어 데스크톱 임시 포트가 TIME_WAIT(기본 120초)에 묶이고, `connectex: Only one usage of each socket address` 오류로 요청이 서버에 닿지 못한다(2026-09-27 2,250/s 회차에서 30%). 과부하 회차 전 데스크톱 관리자 PowerShell에서 `netsh int ipv4 set dynamicport tcp start=10000 num=55535`(즉시 적용)와 `TcpTimedWaitDelay` = 30(재부팅 필요)으로 늘리고, 측정 뒤 되돌린다. k6 요약의 `orders_failed_other`와 run.log의 `Request Failed`가 0인지 매 회차 확인한다. 실서비스에서는 거절된 클라이언트의 재연결(TLS면 핸드셰이크)이 과부하 순간에 서버 부담을 더한다는 뜻이기도 하다. 2026-09-28부터 거절은 연결을 유지하는 429가 기본이라 거절 때문에 끊기는 연결은 없다(400·500 등 다른 오류는 여전히 끊긴다).
- **노트북 유입 한계(약 2,620~2,950건/s, 회차마다 갈림)**: 2026-09-27 기록 — 채택 설정(single P=20·k=20)에서 3,000/s는 3회 모두 정상, 3,300·3,600/s는 저하(접수 p95 206·269ms, 저장량 감소), 4,000/s는 붕괴(TCP 연결 시간 초과 7,502건, p95 1.24초)였다. 거절이 많은 P=15·k=15는 3,000/s에서도 한 번 무너졌다. 원인은 Tomcat이 503마다 연결을 닫아 재연결이 몰리는 것이었다(429로 바꾸면 4,000/s에서도 무너지지 않고 저하, P2 결과 README 7절). 2026-09-28 429 재측정에서 3,000/s도 6회 중 2회 나쁨, 2,700/s도 2회 중 1회 나쁨으로 깔끔한 문턱이 없었다(P2 결과 README 8절). 한 회차만 보고 용량을 정하지 말고, 같은 부하를 여러 번 재 좋은·나쁜 상태를 모두 기록한다. 유입 한계 근처 회차는 판정(연결 실패 0건, 응답 분포)을 반드시 확인하고, 서버에 GC 기록(`-Xlog:gc`)을 켜 둔다.
- **IP 변동**: WiFi↔유선 전환 시 IP 바뀜. 회차마다 확인.
- **네트워크 대역폭**: S·C처럼 유입이 가장 큰 회차 동안 노트북 작업 관리자 > 성능 > 이더넷의 송수신량을 본다. 링크의 70%(100 Mbps면 약 70 Mbps)를 넘으면 네트워크가 결과에 섞였을 수 있으므로 그 회차를 표시한다. 바닥값(`/ping`)의 p99가 수 ms 이내로 안정적인지도 함께 본다. 두 장비가 기가비트를 지원하는데 100 Mbps로 연결되면 케이블(Cat5e/Cat6)·포트를 먼저 의심한다.
- **① 회차 준비**: shops 통계 컬럼 0 초기화(reset 하단) 없이 기동하면 NPE로 전부 500이 난다. 데드락과 구분되지 않으므로 반드시 먼저 실행.
- **① 회차 판정**: threshold 통과·실패로 판정하지 않는다. lock_deadlocks 증가분과 5xx 건수로.
- **closed model 처리량 착시**: 실패 응답이 빨리 돌아오면 iterations 가 부풀어 보인다. 처리량은 2xx(`reviews_created`)만 센다.
- **테스트 실행 금지(OMP)**: 통합 테스트는 마스터 테이블을 전부 지운다. 8절대로 OMP_TEST에서만.
- **actuator 노출**: 현재 `management.endpoints.web.exposure.include=*` — 벤치마크 편의용이므로 외부 배포 시 축소.
- **테이블명 대소문자**: Windows MySQL은 대소문자 무시. 서버를 Linux로 옮기면 소문자 테이블명 기준으로 SQL 확인.
- **`.env` 미적용**: 리포 루트가 아닌 곳에서 기동하면 `.env`를 못 찾고 오류 없이 기본값으로 뜬다. 값에 따옴표·뒤 공백을 넣지 않는다(Java properties 형식으로 읽음). 회차마다 0절 확인 명령으로 P·k·Q를 기록한다.

## 8. 테스트 실행 (측정과 무관, 코드 수정 검증)

```bash
./gradlew test --console=plain
```

- DB 비밀번호는 리포 루트 `.env`의 `OMP_DB_PASSWORD`를 읽는다. 풀 크기·리뷰 모드 등 튜닝 값은 `application-test.properties`가 고정하므로 `.env`의 파일럿 값이 테스트에 영향을 주지 않는다.

- 프로필 `test` → `src/test/resources/application-test.properties` → DB **`OMP_TEST`** (`createDatabaseIfNotExist=true`, `ddl-auto=create`).
- `TestFixtures.resetAndSeed`는 users/shops/carts를 전부 지우기 전에 `DATABASE()`가 `OMP_TEST`인지 확인하고 아니면 예외로 중단한다. 프로필 파일이 없으면 기본 설정(OMP)으로 붙으므로 이 가드가 seed 데이터를 지킨다.
- `bench/review-before`에서는 `ReviewStats*Test`가 실패한다 (통계 테이블을 쓰지 않는 설계). 그 브랜치는 벤치마크 전용이다.
