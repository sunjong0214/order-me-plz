-- 테스트 종료 후 검증 (리뷰 테스트는 비동기 갱신 소진을 위해 종료 1~2분 뒤 실행)
USE OMP;

-- 1) 데드락 카운터 (테스트 시작 전에도 실행해 두고, 전/후 값의 차이 = 테스트 중 발생 건수)
SELECT name, count AS deadlocks
FROM information_schema.INNODB_METRICS
WHERE name = 'lock_deadlocks';

-- 2) 통계 정합성: 0행이면 접수된 모든 리뷰가 통계에 정확히 반영됨.
--    count와 sum 모두 완전 일치(exact) 검증 — 원자 증가 연산이므로 오차 허용이 필요 없다.
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

-- 3) 주문 테스트: 저장 완료량 (reset-round.sql로 비운 뒤 측정했으므로 COUNT = 완료량)
SELECT COUNT(*) AS completed_orders FROM orders;

-- [bench/review-before 회차 전용] 구 설계의 유실 확인:
-- SELECT s.shop_id, s.review_count,
--        (SELECT COUNT(*) FROM reviews r WHERE r.shop_id = s.shop_id) AS actual_count
-- FROM shops s
-- WHERE s.review_count <> (SELECT COUNT(*) FROM reviews r WHERE r.shop_id = s.shop_id);

-- 2-b) 리뷰는 있는데 통계 행이 아예 없는 가게 (2번 쿼리는 통계 테이블 기준 LEFT JOIN이라 이 경우를 놓친다). 0행이어야 함.
SELECT r.shop_id, COUNT(*) AS reviews_without_stats_row
FROM reviews r
LEFT JOIN shop_review_stats s ON s.shop_id = r.shop_id
WHERE s.shop_id IS NULL
GROUP BY r.shop_id;

-- 2-c) 평균 검증. average_rating은 DECIMAL(38,2), 매 UPDATE마다 (sum + r) / (count + 1)을 소수 2자리로 저장하므로
--      최종값은 sum/count를 소수 2자리로 반올림한 값과 같아야 한다. 0행이어야 함.
SELECT s.shop_id, s.average_rating, ROUND(s.rating_sum / s.review_count, 2) AS expected_average
FROM shop_review_stats s
WHERE s.review_count > 0
  AND s.average_rating <> ROUND(s.rating_sum / s.review_count, 2);
