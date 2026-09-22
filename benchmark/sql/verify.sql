-- 측정 전·후 검증.
--   시작 전 : 1) 1-b) 를 실행해 값을 기록한다. 종료 후 값과의 차이 = 측정 중 발생 건수.
--   종료 후 : 완료 조건(insert·reviewStats executor 의 queued=0·active=0, COUNT(*) 정지)을 확인한 뒤 전체 실행.
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
--    0행이면 접수된 모든 리뷰가 통계에 정확히 반영됨. count 와 sum 모두 완전 일치(exact) 검증 — 원자 증가 연산이므로 오차 허용이 필요 없다.
--    ③에서 omp.review.stats.rejected / failed 증가분이 N 이면 여기 N 건의 불일치가 남아야 한다 (재처리 없음 → 불일치 지속).
--    ① bench/review-before 회차에서는 shop_review_stats 를 갱신하지 않으므로 전부 불일치로 나온다. 그 회차는 4) 를 쓴다.
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
--    4-a) 저장된 리뷰 vs shops 통계. 불일치 행 = 읽고-계산-쓰기 유실(같은 가게 겹침) 결과.
--         데드락으로 롤백된 요청은 리뷰도 함께 롤백되므로 여기 잡히지 않고 5xx 건수와 lock_deadlocks 증가분으로 본다.
-- SELECT s.shop_id, s.review_count, COALESCE(r.actual_count, 0) AS actual_count,
--        s.rating_sum, COALESCE(r.actual_sum, 0) AS actual_sum
-- FROM shops s
-- LEFT JOIN (SELECT shop_id, COUNT(*) AS actual_count, SUM(rating) AS actual_sum FROM reviews GROUP BY shop_id) r
--        ON r.shop_id = s.shop_id
-- WHERE s.review_count <> COALESCE(r.actual_count, 0)
--    OR s.rating_sum   <> COALESCE(r.actual_sum, 0);
--    4-b) 최근 데드락 1건의 락 정보 캡처 (결과 문서에 첨부). innodb_print_all_deadlocks=ON 이면 에러 로그에 전건 기록.
-- SHOW ENGINE INNODB STATUS;
