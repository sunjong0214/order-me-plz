-- 이미 seed.sql(회원·장바구니 10만)을 넣은 DB를 30만으로 늘린다. 선착순 할인 측정(03-promotion-flash.js) 전 1회.
-- cart N = user N 을 지키려고 id를 직접 넣는다(가게 배정은 seed.sql과 같은 1 + (N mod 1000)).
USE OMP;

SET SESSION cte_max_recursion_depth = 400000;

INSERT INTO users (user_id, email, name, status)
WITH RECURSIVE seq AS (SELECT (SELECT MAX(user_id) + 1 FROM users) AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 300000)
SELECT n, CONCAT('user', n, '@test.com'), CONCAT('user', n), 'GENERAL' FROM seq;

INSERT INTO carts (cart_id, user_id, shop_id)
WITH RECURSIVE seq AS (SELECT (SELECT MAX(cart_id) + 1 FROM carts) AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 300000)
SELECT n, n, 1 + (n MOD 1000) FROM seq;

SELECT (SELECT COUNT(*) FROM users) AS users, (SELECT MAX(user_id) FROM users) AS max_user,
       (SELECT COUNT(*) FROM carts) AS carts, (SELECT MAX(cart_id) FROM carts) AS max_cart;
