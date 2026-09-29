# 격리 수준 비교 (2026-09-29 10:19~12:08)

주문 트랜잭션에 `READ_COMMITTED`를 코드로 지정하던 것(기존)을 빼기 전에, 차이를 쟀다. 결과로 지정을 뺐다(`a278f7f`).

- 조건: 비동기 `single`, P=20·k=20, 큐 70,000칸, 거절 429, **초당 2,000건 고정 3분**(용량 아래라 흔들림이 적다)
- 순서: 기존 → 기본값 → RC 기본 → RC 기본 → 기본값 → 기존 (시간대 효과가 변형 효과처럼 보이지 않게)
- 통제: 회차마다 15분 휴식, 충전기 확인, 노트북 클럭·CPU 기록, 부하기 CPU 판정 (P2와 같음)

| 변형 | 뜻 |
|---|---|
| 기존 (`rc`) | 코드에서 `@Transactional(isolation = READ_COMMITTED)`. MySQL 기본(RR)과 달라 트랜잭션마다 격리 수준을 바꾸고 확인하고 되돌린다 |
| 기본값 (`def`) | 지정 없음 → MySQL 기본 REPEATABLE READ. 전환 없음 |
| RC 기본 (`rcpool`) | 지정 없음 + 커넥션 기본값 READ COMMITTED(`spring.datasource.hikari.transaction-isolation`). 전환 없이 RC |

## 결과

| 회차 | 변형 | 주문당 MySQL 명령 (SELECT / SET) | 접수 p95 / p99 | 저장 완료 p95 / p99 | 노트북 CPU (mysqld / java) | 앱 커넥션 세션 격리 |
|---|---|---|---|---|---|---|
| 01 | 기존 | 9 (2 / 4) | 6.3 / 13.9ms | 9.0 / 22.6ms | 99.1% (60.1 / 32.0) | REPEATABLE-READ |
| 06 | 기존 | 9 (2 / 4) | 8.3 / 18.8ms | 9.2 / 23.0ms | 99.2% (60.0 / 32.3) | REPEATABLE-READ |
| 02 | 기본값 | **6 (1 / 2)** | **3.2 / 10.1ms** | **4.9 / 17.7ms** | 97.5% (61.2 / 28.5) | REPEATABLE-READ |
| 05 | 기본값 | 6 (1 / 2) | 3.6 / 11.6ms | 4.9 / 17.8ms | 97.4% (61.0 / 28.7) | REPEATABLE-READ |
| 03 | RC 기본 | 6 (1 / 2) | 4.1 / 13.1ms | 4.9 / 17.7ms | 97.7% (61.6 / 28.4) | READ-COMMITTED |
| 04 | RC 기본 | 6 (1 / 2) | 4.2 / 13.6ms | 4.9 / 18.2ms | 97.6% (61.4 / 28.6) | READ-COMMITTED |

모든 회차: 접수 36만 건, 거절·실패·보내지 못한 요청 0, DB 주문 수 = 접수, 행 잠금 대기 0, TCP 재전송 0, 데스크톱 CPU 평균 14~17%.

- **차이를 만든 것은 격리 수준 전환 명령이다.** 코드로 지정하면 트랜잭션마다 SET 2개(설정·복원)와 SELECT 1개(현재 값 확인)가 붙어 주문당 명령이 9개, 빼면 6개다. java CPU가 약 3.5%p 낮아지고, 접수 p95는 약 절반, 저장 완료 p95는 9 → 4.9ms가 됐다
- **격리 수준 자체(기본값 RR vs RC 기본)는 거의 같다.** 명령 수와 저장 완료가 같고, 접수 p95만 3.2~3.6 vs 4.1~4.2ms로 작은 차이다. 이 트랜잭션은 읽기가 검증 SELECT 한 번뿐이고 잠그며 읽는 범위 조건이 없어 두 격리 수준의 결과가 같다
- 앱 커넥션의 세션 격리 수준(워밍업 뒤, 트랜잭션 밖)은 기존·기본값 모두 REPEATABLE-READ다. 기존은 트랜잭션마다 RC로 바꿨다가 끝날 때 되돌리기 때문이다. RC 기본은 커넥션이 처음부터 READ-COMMITTED다
- 노트북 CPU는 용량의 약 70%인 이 부하에서도 97~99%였다. CPU 사용률로는 비용 차이를 가르기 어려워 명령 수와 지연으로 비교했다(MySQL이 남는 CPU를 커밋 대기에 쓰는 것으로 보이나 확인하지 않았다)
- 주문당 redo fsync는 기존 1.66~1.70회, 나머지 2.12~2.14회다. 트랜잭션이 빨라져 같은 부하에서 동시에 커밋하는 주문이 줄고 커밋 묶음이 작아진 것으로 본다

**결론:** 주문 트랜잭션의 격리 수준 지정을 빼고 DB 기본값을 쓴다(`a278f7f`). 그 뒤 선착순 할인의 DB 여러 행 방식만 테스트에서 찾은 교착을 근거로 READ COMMITTED를 지정했다(`benchmark/DESIGN-promotion-stock.md` 3.3).

## 파일

- `01-rc` ~ `06-rc`: k6 요약 JSON, `run.log`·`warmup.log`, `server-metrics.csv`, `server-hist.txt`, `client-cpu.csv`, `counters.csv`, `drain.txt`, `db-orders.txt`, `mysql-status.csv`, `isolation.txt`(앱 커넥션의 세션 격리 수준), `gc.log`는 없음
- `laptop-cpu-iso.csv`, `series-iso.log`: 노트북 5초 기록, 회차 진행 기록
- 이전 빌드는 노트북의 `build/libs/OrderMePlz-rc.jar`로 띄웠다
