# k6 재측정 가이드 (Order-Me-Plz)

부하 생성기 = **데스크탑(유선)**, 서버+MySQL = **노트북(유선 연결)** 기준. k6는 데스크탑에만 설치한다.

```bash
winget install k6 --source winget   # 또는 choco install k6
k6 version                          # 환경 표에 기록
```

선행 문서: [REVIEW-2026-09-08.md](REVIEW-2026-09-08.md) (측정 설계 검토), [STEP1-2026-09-19.md](STEP1-2026-09-19.md) (측정 전 코드 수정).
2026-09-22 리뷰 포트폴리오 검토 반영: 책임 분리와 트랜잭션 분리의 구분, 결함별 재현 계획, 두 장비 유선 환경, 지표·판정 기준을 정리했다. 리뷰 원인 재현 테스트와 본측정은 아직 완료되지 않았다.
2026-09-24 리뷰 통계 반영 방식을 요구사항 우선순위로 확정했다: **② 같은 트랜잭션 채택(기본 모드 sync), ③ 비동기는 비교군.** 근거와 검증 기준은 2절 "리뷰: 설계 판단 기준".

---

## 0. 빌드·기동 (서버 노트북)

| 항목 | 값 |
|---|---|
| JDK | 21 (`java -version` 확인. 빌드 toolchain도 21) |
| 빌드 | `./gradlew bootJar`. `gradle/wrapper/`는 gitignore 대상이라 클론에 없으면 `gradle wrapper --gradle-version 8.14.3`으로 생성하거나 시스템 Gradle 사용 |
| DB 비밀번호 | `OMP_DB_PASSWORD` 환경변수 (미지정 시 1234) |
| 리뷰 통계 모드 | `omp.review.stats.mode` = `sync`(기본, 채택) / `async`(비교군). 기동 인자로 덮어쓴다 |
| 힙 | `-Xms2g -Xmx2g` 고정 (리사이즈 노이즈 제거) |

```bash
./gradlew bootJar
OMP_DB_PASSWORD=<비번> java -Xms2g -Xmx2g -jar build/libs/OrderMePlz-0.0.1-SNAPSHOT.jar
# 리뷰 ③ 회차만: 위 명령 끝에  --omp.review.stats.mode=async
```

## 1. 무엇을 무엇과 비교하는가

| 측정 | 브랜치 | 기동 옵션 | 스크립트·옵션 | 목적 |
|---|---|---|---|---|
| 바닥값 | main | | `00-network-floor.js` | `/ping`. 해석 참고선. 1회 |
| 주문 · 개선 전 (동기) | main | | `01-order-api.js -e MODE=sync` | 회차 A~C. 같은 빌드에 두 엔드포인트 공존 |
| 주문 · 개선 후 (비동기 접수) | main | | `01-order-api.js` (MODE=async) | 회차 A~D. 초안 [PORTFOLIO-1-2-draft.md](PORTFOLIO-1-2-draft.md) |
| 리뷰 ① shops 통계 + 같은 트랜잭션 | `bench/review-before` | | `02-review-api.js -e MODEL=closed -e TAG=before -e THRESHOLDS=off` | 데드락·유실 재현 |
| 리뷰 ② 별도 통계 + 같은 트랜잭션 **(채택)** | main | (기본) | `02-review-api.js -e MODEL=closed -e TAG=sync` | ①→② = 모델 분리 + 원자 갱신의 결합 효과. 채택 검증(2절) |
| 리뷰 ③ 별도 통계 + AFTER_COMMIT 비동기 (비교군) | main | `--omp.review.stats.mode=async` | `02-review-api.js -e MODEL=closed -e TAG=async` | ②→③ = 트랜잭션 분리 + 비동기 실행의 결합 효과 (응답 지연 이득 vs 반영 지연·누락 경로). 채택 여부를 가르지 않음 |

- 주문 전/후는 checkout 없이 같은 서버에서 URL만 바꾼다. 단, 동기 응답은 **저장 완료**까지, 비동기 응답은 **접수**까지라 계약이 다르다. 접수 지연과 커밋 완료량을 따로 기록하고 "저장 속도 개선"으로 쓰지 않는다.
- `bench/review-before`는 **main에서 분기**해 `Shop`의 통계 필드 3개와 `ReviewService.saveReviewBy`를 바꾼 재구성 브랜치다. 애플리케이션의 스레드 풀·검증·예외 처리·설정은 같지만, ①→②에서는 통계 저장 위치와 갱신 방식이 함께 바뀐다. 모델 분리만의 성능 효과라고 해석하지 않는다. 옛 05ad2d8 기반 브랜치는 `bench/review-before-legacy-05ad2d8`로 남겨 두었고 측정에 쓰지 않는다 (CallerRuns 풀·Spring Retry·writerId 미할당이 섞여 비교가 오염된다).
- 리뷰는 ②를 채택했다. ①→②에서 데드락·갱신 유실이 모델 분리와 원자 UPDATE로 해결되므로, ③의 분리는 결함 해결 수단이 아니라 별도의 선택이다. 그 선택은 응답 시간이 아니라 요구사항 우선순위(2절)로 판단했고, 측정은 ②가 그 요구를 만족하는지 검증하고 ③의 이득·대가를 기록하는 데 쓴다.
- 옛 nGrinder 수치와는 도구가 다르므로 비교하지 않는다.

## 2. 측정 유형

1. **고정 rate** (`SCENARIO=fixed`, 기본): "초당 N건 유입 시 p95/p99와 에러율". 전/후 **모두 감당 가능한 rate**로 고정해야 비교가 성립한다.
2. **고정 VU** (`MODEL=closed`, 리뷰 ①②③): VUS명이 응답을 받은 뒤 다음 요청을 보낸다(nGrinder vUser 방식). VU 수를 고정하며, 응답 시간과 스크립트 실행 시간에 따라 실제 유입률은 달라진다. 리뷰는 "같은 VU 수에서 성공 응답 수·p95·실패·정합성"을 비교한다. "같은 유입률에서 응답 지연 개선"을 주장하려면 ②·③에 같은 RATE의 open model을 추가한다.
3. **용량**: "측정 조건에서 503(또는 p95>200ms) 없이 유지한 최대 유입률". k6 summary의 p95는 전체 집계라 램프 한 번으로는 한계 시점을 읽을 수 없다.
   - 탐색: `-e SCENARIO=ramp -e THRESHOLDS=off --out csv=...` (단계마다 RAMP 상승 + HOLD 유지). 시계열에서 HOLD 구간별 p95·503을 계산해 후보 구간을 본다.
   - 확정: 후보 rate마다 `SCENARIO=fixed`를 3~5분씩 따로 돌려 rate별 p95·503 표를 만든다 (계단식 고정 run). 15분 본측정은 확정 rate에서.
4. **포화** (`SCENARIO=saturate`, 풀 크기 파일럿 전용): VUS명이 쉬지 않고 요청해 서버를 포화시킨 상태의 처리량을 잰다. 아래 "풀 크기 산정" 1단계에서만 쓴다.

### 풀 크기 산정 (주문·리뷰 공통, 본측정 전 확정)

서버 노트북: **물리 4코어 / 논리 8 / RAM 16GB.** 앱(JVM)과 MySQL이 같은 CPU를 나눠 쓴다.

원칙
- 풀 크기는 시나리오(평상시·저녁 피크·쿠폰 이벤트)마다 바꾸지 않는다. **하나의 고정 설정**으로 세 상황을 모두 돌려, 그 설정이 모두 감당하는지 검증한다.
- 병목(DB)에서 거꾸로 정한다: 커넥션 총량 P → insert 워커 k → 저장 처리량 W(측정) → insert 큐 Q.
- 공식은 출발점이고 확정은 파일럿 측정으로 한다. 확정값은 `application.properties`에 반영해 모든 본측정(동기 비교군·리뷰 포함)에 같은 값을 쓴다.

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

**파일럿 절차 (본측정 전 1회).** 값은 기동 인자로만 바꾼다. run마다 서버 재시작 → 워밍업 1분 → `reset-round.sql` → 3분 측정. 측정 중 4절 폴링과 mysqld·java CPU 사용률을 함께 기록한다(처리량이 멈추는 원인이 CPU 공유인지 판단).

```bash
S=http://<서버IP>:8080; OUT=benchmark/results

# P1. 커넥션 총량 — 서버: P만 바꿔 4번 기동
#   OMP_DB_PASSWORD=<비번> java -Xms2g -Xmx2g -jar build/libs/OrderMePlz-0.0.1-SNAPSHOT.jar --spring.datasource.hikari.maximum-pool-size=<5|10|15|20>
#   VUS 64: 최대 P(20)보다 충분히 커서 풀이 항상 포화되고, Tomcat 200보다 작아 Tomcat이 제한 요인이 되지 않는다.
k6 run -e BASE_URL=$S -e MODE=sync -e SCENARIO=saturate -e VUS=64 -e DURATION=3m -e THRESHOLDS=off -e TAG=P1-p<P> -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
#   기록: orders_accepted ÷ 180초 = 처리량, p95, hikari_pending, CPU%.

# P2. insert 워커 — 서버: P는 P1 확정값, 큐는 100으로 작게 둬서 접수량 = 완료량이 되게 한다
#   ... --spring.datasource.hikari.maximum-pool-size=<P> --omp.executor.insert.core=<4|6|8> --omp.executor.insert.max=<같은 값> --omp.executor.insert.queue=100
#   RATE: P1 처리량의 1.5배. 503이 나와야 포화된 것이며, 503이 없으면 RATE를 올린다.
k6 run -e BASE_URL=$S -e MODE=async -e RATE=<P1 처리량 × 1.5> -e DURATION=3m -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=P2-k<k> -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
#   기록: orders_accepted ÷ 180초 = W, order_accepted_duration p95(202만), order_rejected_duration p95, 503 수, hikari_pending.

# 확정: application.properties 에 hikari = P, insert core = max = k, insert queue = Q, reviewStats core = max = P/2 를 반영한다.
```

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

**원인 재현은 부하 측정과 별도로 준비한다.** 현재 순차 통계 테스트와 주석 처리된 동시성 테스트만으로 아래 검증이 끝났다고 쓰지 않는다. 재현 테스트는 `OMP_TEST`에서 수행하며 벤치마크 데이터와 분리한다.

| 검증 | 제어할 실행 순서 | 근거 |
|---|---|---|
| ① 데드락 | 같은 가게에 두 트랜잭션이 리뷰 INSERT를 마쳐 S락을 보유한 뒤 shops UPDATE 시도 | 데드락 오류·락 로그와 롤백. 단순한 HTTP 500만으로 판정하지 않음 |
| ① 갱신 유실 | 두 트랜잭션이 같은 이전 통계를 읽은 뒤 첫 번째 INSERT·UPDATE·커밋, 두 번째 INSERT·이전 값 기반 UPDATE·커밋 | 두 리뷰는 저장됐지만 통계에 덮어쓰기가 발생하는지 확인 |
| ② 개선 후 | 같은 리뷰 입력을 동시 실행하고 stats 원자 UPDATE 후 종료 | 최종 리뷰 개수·평점 합계·평균 일치, 기존 락 승격 경로 없음 |
| ②·③ 통계 실패 정책 | 통계 행 누락을 강제해 같은 리뷰 요청 실행. `ReviewStatsMissingRowSyncModeTest`·`ReviewStatsMissingRowAsyncModeTest` 참고 | ②는 리뷰도 롤백, ③은 리뷰 유지 + 통계 실패 기록. 자동 복구를 검증하는 테스트는 아님 |

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

## 3. 실행 순서

### 사전 준비 (1회)
1. 노트북: 전원 연결 + "최고 성능" 전원 계획, 방화벽 8080 인바운드 허용, `ipconfig`로 IP 확인.
2. MySQL: `SET GLOBAL innodb_print_all_deadlocks = ON` (에러 로그에 데드락 전건 기록). `verify.sql` 1)의 `status`가 `enabled`인지 확인.
3. 새 스키마(OMP)로 main 서버 1회 기동 → 테이블 생성 확인 → `sql/seed.sql`.
4. 스모크: RATE 10, 30초로 01·02 실행 → check 실패 0.
5. 결과 폴더 `benchmark/results/`가 있는지 확인. k6는 폴더를 만들지 않으므로 `OUT_DIR`는 존재하는 경로여야 한다.
6. 2절 "풀 크기 산정" 파일럿(P1·P2)으로 P·k·W·Q를 확정하고 `application.properties`에 반영한 뒤 다시 빌드한다. 이후 모든 회차는 이 값으로 고정한다.

### 매 회차 공통 절차 (순서가 결과를 좌우한다)
1. 서버 재시작 (해당 브랜치·기동 옵션). ①을 처음 기동해 shops 통계 컬럼이 NULL이면 워밍업 전에 0으로 초기화한다. 본측정 초기화는 4번에서 다시 한다.
2. **워밍업**: 같은 스크립트로 1~2분 실행하고 결과는 버린다. 리뷰는 `MODEL=open`, 낮은 `RATE`를 명시하거나 `MODEL=closed`에서 `VUS`를 낮춘다. closed 모드에서 RATE만 바꿔도 부하는 줄지 않는다. ①의 오류는 원인별로 확인한다.
3. 워밍업 작업 **소진 확인**: 4절 폴링에서 `queued=0` **그리고** `active=0`.
4. `sql/reset-round.sql` 실행 (워밍업 쓰기 제거). ① 회차는 하단 `UPDATE shops ...`도 실행.
5. **시작 전 값 기록**: `verify.sql` 1)·1-b) (lock_deadlocks, row lock) + 카운터 3종 (`omp.order.async.rejected`, `omp.review.stats.rejected`, `omp.review.stats.failed`). 모두 기동 후 누적값이라 **증가분**으로 쓴다.
6. 4절 폴링 시작 → 본측정 실행.
7. 종료 후 **완료 대기**: `queued=0`·`active=0`이 되고 `COUNT(*)`가 더 변하지 않을 때까지. 제한 5분 초과 시 "미완료"로 기록한다.
8. 해당 설계의 검증 SQL 실행: ①은 1)·1-b)와 주석 해제한 4-a)·4-b), ②·③은 1)·1-b)·2)·2-b)·2-c). 리뷰는 `SELECT COUNT(*) FROM reviews`도 기록한다. 주문은 3)을 사용한다. 서버 로그와 k6 JSON·CSV·폴링 CSV를 `benchmark/results/<날짜>-<TAG>-r<N>/`로 이동.
9. 같은 조건 **3회** → 성능은 중앙값과 범위, 오류·불일치는 **모든 회차의 건수**를 기록. 회차 사이 5분 휴식하고 클럭·온도를 확인한다. 가능하면 리뷰 실행 순서를 ①②③ / ②③① / ③①②로 순환해 특정 설계가 항상 마지막에 측정되는 것을 피한다.
10. 주문 회차 A(계단식)는 탐색용이라 1~5 를 rate 계단마다 반복하지 않는다. 시작 시 한 번 하고, 계단 사이에는 폴링으로 `queued=0`·`active=0` 만 확인한다. B·C·리뷰 ①②③은 1~9 전부.

### 본측정 명령 (데스크탑, 리포 루트에서)
```bash
S=http://<서버IP>:8080; OUT=benchmark/results

# ── 주문: 회차 A~D (PORTFOLIO-1-2-draft.md 검증 방법). 전부 open model. 모드는 MODE=sync / async, 같은 서버에서 URL만 다름.

# A. 접수 용량 — 계단식 고정 run. rate 마다 3~5분, sync·async 각각. THRESHOLDS=off (한계를 넘기는 게 목적. 판정은 결과값으로)
#    "p95 < 200ms, 실패 0, 503 0" 을 지키는 최대 rate = 모드별 접수 용량. A 는 워밍업·reset 을 rate 계단마다 반복하지 않고 한 번만 한다.
for R in 300 500 800 1000 1500; do
  k6 run -e BASE_URL=$S -e RATE=$R -e DURATION=4m -e MODE=sync  -e THRESHOLDS=off -e TAG=A-r$R -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
done
for R in 300 500 800 1000 1500; do
  k6 run -e BASE_URL=$S -e RATE=$R -e DURATION=4m -e MODE=async -e THRESHOLDS=off -e TAG=A-r$R -e OUT_DIR=$OUT benchmark/k6/01-order-api.js
done
#    (탐색만 빠르게 하려면 -e SCENARIO=ramp --out csv=... 로 한 번 훑고, 후보 rate 만 위 고정 run 으로 확정)

# B. 같은 유입량 비교 — B_RATE = A 에서 확인한 sync 용량 근처. 15분 × 3회(TAG 의 r1~r3). THRESHOLDS=strict(기본): 둘 다 지켜야 비교 성립
B_RATE=<A에서 확정>
k6 run -e BASE_URL=$S -e RATE=$B_RATE -e DURATION=15m -e MODE=sync  -e TAG=B-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_sync_B-r1.csv  benchmark/k6/01-order-api.js
k6 run -e BASE_URL=$S -e RATE=$B_RATE -e DURATION=15m -e MODE=async -e TAG=B-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_async_B-r1.csv benchmark/k6/01-order-api.js

# C. 초과 유입 — C_RATE > sync 용량 (예: sync 용량의 1.5배). 15분. THRESHOLDS=off. MAX_VUS 를 넉넉히 (dropped 가 서버 포화인지 부하기 한계인지 구분).
#    sync: p95·max·dropped·5xx 로 붕괴 양상. async: orders_rejected(503) == omp.order.async.rejected 증가분, orders_accepted == completed_orders.
C_RATE=<A에서 확정>
k6 run -e BASE_URL=$S -e RATE=$C_RATE -e DURATION=15m -e MODE=sync  -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=C-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_sync_C-r1.csv  benchmark/k6/01-order-api.js
k6 run -e BASE_URL=$S -e RATE=$C_RATE -e DURATION=15m -e MODE=async -e THRESHOLDS=off -e MAX_VUS=4000 -e TAG=C-r1 -e OUT_DIR=$OUT --out csv=$OUT/order_async_C-r1.csv benchmark/k6/01-order-api.js

# D. 완료 추적 — 별도 명령 없음. B·C 의 async 회차 동안 4절 폴링 CSV 를 켜 두고, k6 종료 후 queued=0·active=0 시각과 COUNT(*) 정지, hikari_pending 최대를 기록.

# 리뷰: 각 명령 전에 해당 서버 브랜치·모드로 공통 절차 1~5를 수행한다. 세 명령을 같은 서버 모드에서 연속 실행하지 않는다.
#   TAG는 결과 파일 이름일 뿐 서버 모드를 바꾸지 않는다. VUS·SHOP_POOL은 파일럿 확정값으로 세 설계에 동일 적용.
#   아래는 r1 예시. r2·r3도 각각 초기화 후 실행한다. 성공 응답 지연은 reviews_success_duration 으로 요약에 나오므로 CSV는 필요 없다.
# ① bench/review-before
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=before-r1 -e OUT_DIR=$OUT -e THRESHOLDS=off benchmark/k6/02-review-api.js
# ② main, 기본 기동 (채택. 2절 채택 검증 조건으로 판정)
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=sync-r1 -e OUT_DIR=$OUT benchmark/k6/02-review-api.js
# ③ main, --omp.review.stats.mode=async (비교군)
k6 run -e BASE_URL=$S -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=async-r1 -e OUT_DIR=$OUT benchmark/k6/02-review-api.js
# ②의 p95만 한도를 넘으면 2절대로 같은 VUS에서 -e SHOP_POOL=1000 회차를 3회 추가한다.
# ③의 대가를 같은 유입률에서 보이려면(선택) ②·③에 MODEL=closed·VUS 대신 MODEL=open과 같은 RATE를 쓴다.
#   RATE는 ②가 감당하는 값(② closed 성공 처리량 이하), SHOP_POOL·DURATION 동일, dropped_iterations=0 확인.
```

### 리뷰 ① (개선 전, `bench/review-before`) 회차
- `git checkout bench/review-before` → `./gradlew bootJar` → 기동. 첫 기동에서 ddl-auto=update가 `shops`에 `review_count`, `rating_sum`, `average_rating`을 추가한다.
- 기존 행의 DECIMAL 컬럼은 NULL이라 `reset-round.sql` 하단 `UPDATE shops SET ... = 0`을 주석 해제해 실행해야 갱신 시 NPE가 나지 않는다.
- **파일럿 1분 먼저.** `-e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=1m -e THRESHOLDS=off -e TAG=before-pilot`에서 시작한다. 데드락 로그로 의도한 원인인지 확인하고, 부하기 여유·서버 자원 사용을 기록한다. 재현이 안 되면 FK·브랜치·초기화와 제어된 재현 테스트부터 확인한 뒤 SHOP_POOL·VUS를 조정한다. 특정 건수의 오류가 나올 때까지 부하를 올리는 것을 목표로 삼지 않는다. 확정 조건은 ①~③에 동일 적용한다.
- closed model 에서는 ①의 데드락 롤백(500)이 빠르게 끝나 처리량이 오히려 높게 보일 수 있다. 처리량은 반드시 `reviews_created`(2xx) 기준으로 비교하고 `iterations` 를 쓰지 않는다.
- **판정은 threshold가 아니다.** `http_req_failed`가 1% 미만이어도 데드락은 발생한다. `lock_deadlocks` 증가분과 락 로그를 근거로 삼고, `reviews_5xx`에는 다른 원인이 섞일 수 있으므로 따로 대조한다. `verify.sql` 4-a)로 저장된 리뷰와 shops 통계의 차이를 확인한다.
- 이 회차는 `shop_review_stats`를 갱신하지 않으므로 2)·2-b)·2-c)를 정합성 근거로 쓰지 않는다. 4-a)와 shops의 평균을 별도로 확인한다.
- 이 브랜치에서 main의 정합성 테스트(`ReviewStats*Test`)는 실패한다. 벤치마크 전용 브랜치다.

## 4. 서버 측 지표 폴링 (k6가 못 재는 것)

5초 간격으로 두 파일을 남긴다. (Git Bash, 종료는 Ctrl+C)
- `server-metrics.csv`: 두 풀의 queued·active, 거절·실패 카운터, Hikari 대기, Tomcat 바쁜 스레드 (시계열)
- `server-hist.txt`: 지연 Timer의 누적 히스토그램 버킷 줄. 누적값이라 두 시점 차이로 임의 구간의 분포를 계산한다 (`benchmark/tools/hist_window.py`)

```bash
S=http://<서버IP>:8080
m() { curl -s "$S/actuator/metrics/$1" | grep -o '"value":[0-9.E+-]*' | head -1 | cut -d: -f2; }
echo "time,insert_queued,insert_active,stats_queued,stats_active,order_rejected,order_failed,stats_rejected,stats_failed,hikari_active,hikari_pending,tomcat_busy" > server-metrics.csv
: > server-hist.txt
while true; do
  T=$(date +%T)
  echo "$T,$(m 'executor.queued?tag=name:insertTaskExecutor'),$(m 'executor.active?tag=name:insertTaskExecutor'),$(m 'executor.queued?tag=name:reviewStatsExecutor'),$(m 'executor.active?tag=name:reviewStatsExecutor'),$(m omp.order.async.rejected),$(m omp.order.async.failed),$(m omp.review.stats.rejected),$(m omp.review.stats.failed),$(m hikaricp.connections.active),$(m hikaricp.connections.pending),$(m tomcat.threads.busy)" | tee -a server-metrics.csv
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

## 5. 결과 읽는 법

| 지표 | 의미 |
|---|---|
| `iterations` | **시도** 수. 실패·503 포함. 접수량이 아니다 |
| `orders_accepted` / `reviews_created` | 주문은 접수 응답 수(202+Location), 리뷰는 저장 성공 응답 수(2xx). ③의 리뷰 성공 응답은 통계 반영 완료를 뜻하지 않음 |
| 리뷰 15분 처리량 | `reviews_created` ÷ 15분 (closed model). ①②③ 같은 VUS 에서 비교 |
| `orders_rejected` | 503 수. 서버 `omp.order.async.rejected` **증가분**과 같아야 한다 |
| `orders_failed_other` / `reviews_5xx` | 그 외 실패 / 리뷰 5xx. 데드락 외 원인을 포함할 수 있으므로 로그와 대조 |
| `completed_orders` (verify 3) | 커밋 완료량. **완료 시점 이후** 값. `orders_accepted` = `completed_orders` + `omp.order.async.failed` 증가분이어야 한다 |
| `http_req_duration` p95/p99 | 응답 종류가 섞인 전체 지연. 판정에는 아래 Trend를 쓴다 |
| `order_accepted_duration` | 주문 성공 응답만의 지연. 동기는 저장 완료(200)까지, 비동기는 접수(202)까지. **접수 p95 ≤ 200ms 판정** |
| `order_rejected_duration` | 503 거절 응답의 지연. 거절도 빨라야 백프레셔가 성립한다 (거절은 검증 SELECT 뒤에 일어난다) |
| `reviews_success_duration` | 리뷰 2xx 응답만의 지연. **② 채택 검증(p95 ≤ 500ms)** |
| `omp.order.async.completion` 등 서버 Timer | 4절 참고. 비동기 저장 완료 p99 ≤ 30초 판정은 `hist_window.py --slo 30` |
| `dropped_iterations` | **0**이어야 "초당 N건 유입 유지" 주장 성립. 쌓이면 `MAX_VUS` 부족 또는 서버 포화 |
| `checks` | strict 회차에서 100% |

해석 노트:
- 바닥값은 참고선이다. **"API p95 − ping p95 = 서버 처리 시간"** 같은 뺄셈은 하지 않는다 (서로 다른 분포의 백분위수는 뺄 수 없다). 서버 내부 구간 시간은 별도 계측이 필요하다.
- 비동기 주문에서 503이 나오면 insertTaskExecutor 포화 → 접수 거절(백프레셔). "측정 조건에서 503 없이 유지한 유입률"이 접수 용량이다. dropped_iterations·p95·다른 오류·완료 결과를 함께 확인한다. (CallerRunsPolicy는 제거됨. 포화 시 톰캣 스레드가 몰래 INSERT하는 구간은 없다.)
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
| 측정일시 / 회차 / TAG | 2026-XX-XX / N회차 (3회 중) / 예: B-r1 (주문 A~D, 리뷰 before·sync·async) |
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
