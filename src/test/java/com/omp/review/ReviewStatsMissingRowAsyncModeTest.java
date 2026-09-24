package com.omp.review;

import static com.omp.support.TestFixtures.SHOP_OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** ASYNC 모드(비교군): 리뷰는 이미 커밋됐으므로 유지되고, 통계 실패는 카운터로 관측된다(재처리는 없다 = 불일치가 지속된다). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "omp.review.stats.mode=async")
class ReviewStatsMissingRowAsyncModeTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
        jdbc.update("DELETE FROM shop_review_stats WHERE shop_id = ?", SHOP_OPEN);
    }

    @Test
    void 통계_행이_없으면_리뷰는_남고_실패_카운터가_오른다() {
        double before = failed();

        assertThat(ReviewStatsModeSupport.postReview(rest, 5).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(TestFixtures.count(jdbc, "reviews")).isEqualTo(1);
        assertThat(TestFixtures.awaitUntil(() -> failed() - before == 1.0, Duration.ofSeconds(5))).isTrue();
    }

    private double failed() {
        var c = meterRegistry.find("omp.review.stats.failed").counter();
        return c == null ? 0.0 : c.count();
    }
}
