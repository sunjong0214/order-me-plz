package com.omp.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.omp.review.ReviewStatsModeSupport.Stats;
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

/** ASYNC 모드(비교군). 리뷰 커밋 후 별도 스레드·트랜잭션에서 통계를 갱신하므로 최종적으로 일치해야 한다. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "omp.review.stats.mode=async")
class ReviewStatsAsyncModeTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 커밋_후_통계가_최종적으로_일치하고_거절은_없다() {
        long lagBefore = lagCount();
        assertThat(ReviewStatsModeSupport.postReview(rest, 5).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ReviewStatsModeSupport.postReview(rest, 1).getStatusCode()).isEqualTo(HttpStatus.OK);

        boolean converged = TestFixtures.awaitUntil(
                () -> ReviewStatsModeSupport.stats(jdbc).count() == 2L, Duration.ofSeconds(5));
        assertThat(converged).isTrue();

        Stats s = ReviewStatsModeSupport.stats(jdbc);
        assertThat(s.sum()).isEqualByComparingTo("6");
        assertThat(s.average()).isEqualByComparingTo("3");

        var rejected = meterRegistry.find("omp.review.stats.rejected").counter();
        assertThat(rejected == null ? 0.0 : rejected.count()).isZero();

        // 반영 지연 Timer: 성공한 통계 갱신마다 1건 (기록은 커밋 직후라 폴링으로 기다린다)
        assertThat(TestFixtures.awaitUntil(() -> lagCount() - lagBefore == 2, Duration.ofSeconds(5))).isTrue();
    }

    private long lagCount() {
        var lag = meterRegistry.find("omp.review.stats.lag").timer();
        return lag == null ? 0 : lag.count();
    }
}
