package com.omp.shop;

import static com.omp.support.TestFixtures.SHOP_OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MySQL은 단일 테이블 UPDATE의 대입을 왼쪽부터 평가하고 뒤의 대입은 이미 갱신된 컬럼 값을 본다.
 * 합계·개수를 먼저 증가시킨 뒤 (합계 + r) / (개수 + 1)로 평균을 구하면 증가분이 두 번 반영된다.
 * H2로는 재현되지 않으므로 실제 MySQL에서 커밋된 값으로 판정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ShopReviewStatsRepositoryTest {

    @Autowired ShopReviewStatsRepository repository;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 평점_5_뒤_1이면_개수2_합계6_평균3이어야_한다() {
        tx.executeWithoutResult(s -> repository.applyReviewRating(SHOP_OPEN, new BigDecimal("5")));
        tx.executeWithoutResult(s -> repository.applyReviewRating(SHOP_OPEN, new BigDecimal("1")));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT review_count, rating_sum, average_rating FROM shop_review_stats WHERE shop_id = ?", SHOP_OPEN);

        assertThat(((Number) row.get("review_count")).longValue()).isEqualTo(2L);
        assertThat((BigDecimal) row.get("rating_sum")).isEqualByComparingTo("6");
        assertThat((BigDecimal) row.get("average_rating")).isEqualByComparingTo("3");
    }
}
