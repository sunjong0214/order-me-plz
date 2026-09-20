package com.omp.review;

import static com.omp.support.TestFixtures.SHOP_OPEN;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

final class ReviewStatsModeSupport {
    private ReviewStatsModeSupport() {}

    static ResponseEntity<String> postReview(TestRestTemplate rest, int rating) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"title\": \"t\", \"writerId\": 1, \"shopId\": " + SHOP_OPEN
                + ", \"detail\": \"d\", \"rating\": " + rating + "}";
        return rest.postForEntity("/api/v1/reviews", new HttpEntity<>(body, headers), String.class);
    }

    static Stats stats(JdbcTemplate jdbc) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT review_count, rating_sum, average_rating FROM shop_review_stats WHERE shop_id = ?", SHOP_OPEN);
        return new Stats(((Number) row.get("review_count")).longValue(),
                (BigDecimal) row.get("rating_sum"), (BigDecimal) row.get("average_rating"));
    }

    record Stats(long count, BigDecimal sum, BigDecimal average) {}
}
