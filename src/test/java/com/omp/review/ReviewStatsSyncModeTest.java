package com.omp.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.omp.review.ReviewStatsModeSupport.Stats;
import com.omp.support.TestFixtures;
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

/** 통계 갱신을 리뷰 INSERT와 같은 트랜잭션에서 수행하는 모드. 응답이 돌아온 순간 통계가 일치해야 한다. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "omp.review.stats.mode=sync")
class ReviewStatsSyncModeTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 응답_직후_통계가_즉시_일치한다() {
        assertThat(ReviewStatsModeSupport.postReview(rest, 5).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ReviewStatsModeSupport.postReview(rest, 1).getStatusCode()).isEqualTo(HttpStatus.OK);

        Stats s = ReviewStatsModeSupport.stats(jdbc);
        assertThat(s.count()).isEqualTo(2L);
        assertThat(s.sum()).isEqualByComparingTo("6");
        assertThat(s.average()).isEqualByComparingTo("3");
    }
}
