-- 측정 전·후 검증.
--   시작 전 : 1) 1-b) 를 실행해 값을 기록한다. 종료 후 값과의 차이 = 측정 중 발생 건수.
--   종료 후 : 작업 종료 조건(executor queued=0·active=0, 원본 행 수·통계 안정)을 확인한 뒤 해당 설계의 쿼리 실행.
--             ①은 1)·1-b)·주석 해제한 4), ②·③은 1)·1-b)·2)·2-b)·2-c), 주문은 3).
--             리뷰는 SELECT COUNT(*) FROM reviews 를 따로 기록해 reviews_created(2xx)와 대조한다.
--             "1~2분 대기"가 아니라 조건 충족까지 대기하고, 제한 시간(5분) 초과 시 "미완료"로 기록한다.
USE OMP;

-- 1) 데드락 카운터. status 가 enabled 여야 count 가 갱신된다 (disabled 면 SET GLOBAL innodb_monitor_enable='lock_deadlocks').
SELECT name, count AS deadlocks, status
FROM information_schema.INNODB_METRICS
WHERE name = 'lock_deadlocks';

-- 1-b) 행 락 대기 통계 (전·후 차이 기록: waits, time, time_avg)
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';

-- ─────────────────────────────────────────────────────────────
-- 2) 통계 정합성 [main 회차(②·③) 전용]
--    2)·2-b)·2-c) 모두 0행이면 원본 리뷰 집계와 통계가 일치한다. 요청별 누락·중복 검증을 대신하지 않는다.
--    이 쿼리의 행 수는 개수·합계가 불일치하는 가게 수다. ③의 rejected / failed 증가분(갱신 작업 수)과 같지 않다.
--    예: 한 가게에 100건 미반영 → 불일치 1행. 부족분 = 여기의 max(actual_count-review_count,0) 합계 + 2-b)의 리뷰 수 합계.
--    초과분 = 여기의 max(review_count-actual_count,0) 합계. 부족분과 초과분을 상쇄하지 않고 따로 기록한다.
--    ③은 rejected / failed 증가분도 대조한다. 재처리가 없어 작업 종료 뒤 남은 불일치는 지속된 미반영이다.
--    ① bench/review-before 회차는 shop_review_stats 를 갱신하지 않으므로 이 쿼리 대신 4) 를 쓴다.
SELECT s.shop_id,
       s.review_count,
       COALESCE(r.actual_count, 0) AS actual_count,
       s.rating_sum,
       COALESCE(r.actual_sum, 0)   AS actual_sum
FROM shop_review_stats s
LEFT JOIN (SELECT shop_id, COUNT(*) AS actual_count, SUM(rating) AS actual_sum
           FROM reviews
           GROUP BY shop_id) r ON r.shop_id = s.shop_id
WHERE s.review_count <> COALESCE(r.actual_count, 0)
   OR s.rating_sum   <> COALESCE(r.actual_sum, 0);

-- 2-b) 리뷰는 있는데 통계 행이 아예 없는 가게 (2 는 통계 테이블 기준 LEFT JOIN 이라 이 경우를 놓친다). 0행이어야 함.
SELECT r.shop_id, COUNT(*) AS reviews_without_stats_row
FROM reviews r
LEFT JOIN shop_review_stats s ON s.shop_id = r.shop_id
WHERE s.shop_id IS NULL
GROUP BY r.shop_id;

-- 2-c) 평균 검증. average_rating 은 DECIMAL(38,2). 매 UPDATE 마다 (sum + r) / (count + 1) 을 같은 DECIMAL 연산 경로로 계산해
--      소수 2자리로 저장하므로 최종값은 ROUND(sum/count, 2) 와 같아야 한다. 0행이어야 함.
SELECT s.shop_id, s.average_rating, ROUND(s.rating_sum / s.review_count, 2) AS expected_average
FROM shop_review_stats s
WHERE s.review_count > 0
  AND s.average_rating <> ROUND(s.rating_sum / s.review_count, 2);

-- ─────────────────────────────────────────────────────────────
-- 3) 주문 테스트: 커밋 완료량. reset-round.sql 로 비운 뒤 측정했으므로 COUNT = 완료량.
--    orders 와 order_menu 가 같아야 한다 (요청당 메뉴 1건). k6 orders_accepted 와 총건수 비교는 기본 점검이며 요청별 대조는 3단계.
SELECT (SELECT COUNT(*) FROM orders)     AS completed_orders,
       (SELECT COUNT(*) FROM order_menu) AS completed_order_menus;

-- ─────────────────────────────────────────────────────────────
-- 4) [bench/review-before 회차(①) 전용] 구 설계 검증. main 회차에서는 shops 에 컬럼이 없어 에러 → 주석 유지.
--    4-a) 저장된 리뷰 vs shops 통계. 불일치 가게 수와 개수 부족/초과분 합계를 별도로 기록한다.
--         읽고-계산-쓰기 유실 원인은 실행 순서를 제어한 별도 테스트로 확인한다. shops 평균도 별도로 확인한다.
--         데드락으로 롤백된 요청은 리뷰도 함께 롤백된다. 데드락 카운터·락 로그와 5xx를 대조하되 모든 5xx를 데드락으로 세지 않는다.
-- SELECT s.shop_id, s.review_count, COALESCE(r.actual_count, 0) AS actual_count,
--        s.rating_sum, COALESCE(r.actual_sum, 0) AS actual_sum
-- FROM shops s
-- LEFT JOIN (SELECT shop_id, COUNT(*) AS actual_count, SUM(rating) AS actual_sum FROM reviews GROUP BY shop_id) r
--        ON r.shop_id = s.shop_id
-- WHERE s.review_count <> COALESCE(r.actual_count, 0)
--    OR s.rating_sum   <> COALESCE(r.actual_sum, 0);
--    4-b) 최근 데드락 1건의 락 정보 캡처 (결과 문서에 첨부). innodb_print_all_deadlocks=ON 이면 에러 로그에 전건 기록.
-- SHOW ENGINE INNODB STATUS;
