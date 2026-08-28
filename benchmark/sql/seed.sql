-- 벤치마크 더미 데이터 시딩
-- 실행 전제:
--   1) 새 스키마에서 애플리케이션을 1회 기동해 ddl-auto=update가 테이블을 만든 상태
--      (구버전 스키마가 남아 있으면 DROP DATABASE OMP; CREATE DATABASE OMP; 후 재기동)
--   2) 모든 테이블이 비어 있어야 함 (auto_increment가 1부터 시작해야 cart N = user N 이 성립)
USE OMP;

SET SESSION cte_max_recursion_depth = 200000;

-- 회원 100,000명 (k6 USER_POOL 기본값과 일치)
INSERT INTO users (email, name, status)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
SELECT CONCAT('user', n, '@test.com'), CONCAT('user', n), 'GENERAL' FROM seq;

-- 가게 1,000개, 전부 영업 중 (k6 SHOP_POOL 기본값과 일치)
-- 주의: is_open=0이면 비동기 주문이 202 접수 후 백그라운드 검증에서 전부 실패한다.
INSERT INTO shops (name, category, is_open)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000)
SELECT CONCAT('shop', n), ELT(1 + (n MOD 4), 'CHICKEN', 'PIZZA', 'FOOD', 'BEEF'), 1 FROM seq;

-- 가게별 리뷰 통계 행 (0으로 초기화)
INSERT INTO shop_review_stats (shop_id, review_count, rating_sum, average_rating)
SELECT shop_id, 0, 0, 0 FROM shops;

-- 장바구니: cart N = user N (k6 주문 스크립트가 cartId = userId 로 요청)
INSERT INTO carts (user_id, shop_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
SELECT n, 1 + (n MOD 1000) FROM seq;

-- 적재 확인
SELECT (SELECT COUNT(*) FROM users)             AS users,
       (SELECT COUNT(*) FROM shops)             AS shops,
       (SELECT COUNT(*) FROM shops WHERE is_open = 1) AS open_shops,
       (SELECT COUNT(*) FROM shop_review_stats) AS stats,
       (SELECT COUNT(*) FROM carts)             AS carts;
