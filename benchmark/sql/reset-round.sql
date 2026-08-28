-- 회차 간 초기화: 누적 데이터를 비워 매 회차의 조건을 동일하게 만든다.
-- (users/shops/carts 마스터 데이터는 유지)
USE OMP;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE reviews;
TRUNCATE TABLE orders;
TRUNCATE TABLE order_menu;
TRUNCATE TABLE deliveries;
SET FOREIGN_KEY_CHECKS = 1;

UPDATE shop_review_stats SET review_count = 0, rating_sum = 0, average_rating = 0;

-- [bench/review-before 회차 전용]
-- 개선 전 브랜치는 shops 테이블의 구 설계 컬럼을 사용한다.
-- 브랜치로 서버를 1회 기동하면 ddl-auto=update가 컬럼을 추가하므로, 그 후 아래 주석을 풀어 실행:
-- UPDATE shops SET average_rating = 0, review_count = 0, version = 0;
