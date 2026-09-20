package com.omp.review;

import static com.omp.support.TestFixtures.SHOP_OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** SYNC 모드의 약속은 "리뷰와 통계가 같은 트랜잭션에서 일치". 통계 행이 없으면 리뷰 저장도 롤백되어야 한다. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "omp.review.stats.mode=sync")
class ReviewStatsMissingRowSyncModeTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
        jdbc.update("DELETE FROM shop_review_stats WHERE shop_id = ?", SHOP_OPEN);
    }

    @Test
    void 통계_행이_없으면_리뷰도_저장되지_않는다() {
        var res = ReviewStatsModeSupport.postReview(rest, 5);

        assertThat(res.getStatusCode().is5xxServerError()).isTrue();
        assertThat(TestFixtures.count(jdbc, "reviews")).isZero();
    }
}
