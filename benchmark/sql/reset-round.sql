-- 회차 간 초기화: 누적 데이터를 비워 매 회차의 조건을 동일하게 만든다. (users/shops/carts 마스터 데이터는 유지)
--
-- 실행 시점 (README 3절): 서버 재시작 → 워밍업 → 워밍업 작업 소진 확인(executor queued=0·active=0)
--                       → **이 파일** → 시작 전 값 기록(verify.sql 1·1-b, 카운터) → 본측정
-- reset 을 워밍업 앞에 두면 워밍업의 쓰기가 남아 "COUNT(*) = 완료량", "orders_accepted == COUNT(orders)" 비교가 깨진다.
USE OMP;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE reviews;
TRUNCATE TABLE orders;
TRUNCATE TABLE order_menu;
TRUNCATE TABLE deliveries;
SET FOREIGN_KEY_CHECKS = 1;

UPDATE shop_review_stats SET review_count = 0, rating_sum = 0, average_rating = 0;

-- [bench/review-before 회차 전용]
-- 재구성한 개선 전 브랜치는 shops 테이블에 통계 컬럼(review_count, rating_sum, average_rating)을 둔다.
-- 브랜치로 서버를 1회 기동하면 ddl-auto=update 가 컬럼을 추가하는데, 기존 행의 DECIMAL 컬럼은 NULL 이라 갱신 시 NPE 가 난다.
-- 브랜치 회차에서는 아래 주석을 풀어 함께 실행한다. (main 회차에서는 컬럼이 없을 수 있어 실행하면 에러 → 주석 유지)
-- UPDATE shops SET review_count = 0, rating_sum = 0, average_rating = 0;

-- 초기화 확인 (전부 0 이어야 함)
SELECT (SELECT COUNT(*) FROM reviews)    AS reviews,
       (SELECT COUNT(*) FROM orders)     AS orders,
       (SELECT COUNT(*) FROM order_menu) AS order_menus,
       (SELECT COUNT(*) FROM shop_review_stats WHERE review_count <> 0 OR rating_sum <> 0 OR average_rating <> 0) AS stats_not_zero;
