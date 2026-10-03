# Order-Me-Plz

음식 주문·배달 도메인의 백엔드 API입니다. 2025년에 기능을 만들었고(2025.01–08), 2026년에 동시성 결함과 비동기 설계를 원인부터 다시 검증한 뒤 k6로 재측정했습니다(2026.08–10).

**기술:** Java 21 · Spring Boot 3.4 · Spring Data JPA · QueryDSL · MySQL 8.0 · Caffeine · SSE · Micrometer(Prometheus) · k6

## 핵심 개선

**1. 리뷰 통계의 데드락과 갱신 유실**
- 리뷰 INSERT의 외래 키 공유 락이 가게 행의 배타 락으로 승격되며 생기는 데드락과, 읽고-계산-쓰기로 생기는 통계 갱신 유실을 원인별로 나눠 실행 순서를 통제한 테스트로 재현했습니다.
- 통계를 별도 테이블로 분리하고 원자 증가 UPDATE로 바꿔, 같은 트랜잭션에서 두 원인을 없앴습니다.
- 같은 가게에 작성이 몰리는 극단 경합(가상 사용자 50명, 가게 3곳, 15분)에서 재구성한 개선 전 설계 대비 데드락 약 103만 건 → 0, 통계 누락 약 15만 건 → 0. 평소 수준의 부하(초당 20건 고정 유입, 가게 1,000곳에 고르게 분산, 15분)에서는 개선 전 설계도 0건이었습니다.
- 자료: [집중 경합 결과](benchmark/results/2026-10-01-REVIEW/README.md) · [분산 부하 결과](benchmark/results/2026-10-01-REVIEW-real/README.md)

**2. 선착순 한정 수량 주문 (재고 10만 개, 시도 약 23만 건)**
- 재고 판정 3방식(DB 행 하나 · DB 여러 행 · 메모리 번호표)과 접수 방식(동기 · 비동기)을 31회 측정해(유효 24회) 비교했습니다.
- 메모리 번호표 + DB 안전망(유니크 · 외래 키)으로 10만 건 확정이 DB 행 하나 약 90초 · DB 여러 행 52.5–56.7초에서 38–41초로 줄었고(비교군별 중앙값), 세 방식 모두 31회 전부 초과 판매 0이었습니다.
- 동기와 비동기의 포화 처리량은 같았습니다(일반 주문 기준 동기 3,283–3,320 · 비동기 3,157–3,388건/s, [용량 재측정](benchmark/results/2026-09-30-CAP2/README.md)). 그래서 접수는 처리량이 아니라 "재고가 남은 동안 거절하지 않음"을 우선해 비동기를 택했습니다. 동시 처리 상한으로 거절하는 동기는 상한을 190까지 올려도 매진 전에 시도 6,215–13,393건을 거절했습니다. 이 우선순위는 첫 측정 결과를 본 뒤 정했습니다.
- 대가: 주문 하나의 확정은 상한을 둔 동기가 더 빠르고, 메모리 큐라 재시작하면 저장 전의 접수 주문을 잃을 수 있습니다. 메모리 번호표는 서버 한 대를 전제합니다(여러 대면 공용 발급기가 필요하고, 초과 판매는 그때도 DB 제약이 막습니다).
- 자료: [설계](benchmark/DESIGN-promotion-stock.md) · [결과](benchmark/results/2026-09-30-FLASH/README.md)

**3. 가게 목록 캐시의 만료와 무효화**
- 근거 없던 만료 시간 24시간을 평점의 허용 지연과 서비스 규모의 재조회 부하로 계산해 5분으로 다시 정했습니다.
- 영업 상태 · 가게 정보 · 새 가게는 변경 시 커밋 후 무효화하고, 여러 키의 동시 만료는 Jitter로, 인기 키 하나의 동시 로딩은 `sync`로 나눠 대응했습니다. 같은 키에 동시에 들어온 미스 10건이 목록 쿼리 1회로 합쳐지는 것을 기능 테스트 `ShopListCacheTest`로 확인했습니다.
- 자료: [측정 가이드의 "캐시" 절](benchmark/README.md)

## 주문 흐름

```
동기    POST /api/v1/order        → 검증 + 저장(트랜잭션 하나) → 200
비동기  POST /api/v1/order/async  → 대기 자리 예약(DB 없음) → [할인 주문이면 번호표 예약] → 202 + Location
                                   → 워커가 트랜잭션 하나로 검증 + 저장
        결과: SSE  GET /api/v1/order/sse/{uuid}
              폴링 GET /api/v1/order/async/{uuid}  (처리 중 202, 완료 303, 실패 200 + FAILED)
대기 자리가 없으면 DB를 쓰지 않고 429 + Retry-After: 1
```

## API

| 도메인 | 엔드포인트 |
|---|---|
| 사용자 | `POST /api/v1/users`, `GET /api/v1/users/{id}` |
| 가게 | `GET /api/v1/shops?category=&cursor=&pageSize=`(목록, 캐시), `GET /api/v1/shops/{id}`, `POST /api/v1/shops`, `PATCH /api/v1/shops`(본문의 id로 영업 상태 · 이름 · 카테고리 변경) |
| 메뉴 | `GET /api/v1/menu?shopId=&cursor=&pageSize=`, `PATCH /api/v1/menu/{id}` |
| 장바구니 | `GET /api/v1/cart/{id}` |
| 주문 | `POST /api/v1/order`, `POST /api/v1/order/async`, `GET /api/v1/order/{id}`, `GET /api/v1/order/sse/{uuid}`, `GET /api/v1/order/async/{uuid}` |
| 배달 | `GET /api/v1/delivery/{id}` |
| 리뷰 | `POST /api/v1/reviews`, `GET /api/v1/reviews/{id}` |
| 선착순 할인 | 주문 요청에 `promotionId`를 넣으면 할인 주문 경로로 갑니다. 이벤트 생성 · 초기화 · 정합성 확인(`/api/v1/promotions`)은 `OMP_PROMOTION_ADMIN_API=true`일 때만 열립니다 |

[api-docs.md](api-docs.md)는 구현 전(2025.04)에 쓴 API 설계안이라 실제 경로·요청 형식과 다릅니다. 실제 엔드포인트는 위 표가 기준입니다.

## 실행

필요: Java 21, MySQL 8.0 (스키마 `OMP`를 만들어 두면 테이블은 JPA가 만듭니다)

```bash
cp .env.example .env    # DB 접속 정보를 고친다 (.env는 git에 올라가지 않는다)
./gradlew bootRun       # 리포 루트에서 실행해야 .env를 읽는다
```

장바구니를 만드는 API가 없으므로 주문을 해 보려면, 첫 기동으로 테이블이 생긴 뒤 빈 스키마에 [benchmark/sql/seed.sql](benchmark/sql/seed.sql)을 넣습니다.

테스트: `./gradlew test`. 통합 테스트는 로컬 MySQL의 `OMP_TEST` 스키마를 쓰고(없으면 만듭니다), DB 계정은 `.env`에서 읽습니다.

주요 설정값(커넥션 풀, 워커 · 큐 크기, 리뷰 통계 반영 방식, 재고 처리 방식 등)은 [.env.example](.env.example)에 설명과 함께 있습니다.

## 성능 측정

- [benchmark/README.md](benchmark/README.md): k6 시나리오, 회차 절차, 판정 기준
- [benchmark/results/](benchmark/results/): 회차별 원자료(k6 요약, 서버 지표 기록, DB 대조)와 요약 README
- 환경: 서버 노트북(i7-1165G7, RAM 16GB, Spring Boot + MySQL 함께) ↔ 유선 LAN ↔ 부하기 데스크톱(k6)

## 구조

```
src/main/java/com/omp
├── user, menu, cart, delivery   기본 도메인
├── shop         가게, 목록 캐시, 리뷰 통계 테이블과 원자 증가 UPDATE
├── review       리뷰 저장, 통계 반영 방식 분기(sync / async)
├── order        동기·비동기 주문 접수, 워커 저장, SSE · 폴링 상태 전달
├── orderMenu    주문 메뉴
├── promotion    선착순 할인: 메모리 번호표 발급기, DB 재고 방식, 결과 모름 대조
└── config       스레드 풀, 캐시, 예외 처리
```
