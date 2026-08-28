package com.omp.shop;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopReviewStatsRepository extends JpaRepository<ShopReviewStats, Long> {

    // 이전 값을 읽지 않는 단일 원자 UPDATE. 증가 연산은 실행 순서와 무관하게 결과가 같으므로
    // 갱신 유실이 없고, 같은 행 락 승격이 없어 데드락도 발생하지 않는다.
    @Modifying
    @Query("update ShopReviewStats s set "
            + "s.ratingSum = s.ratingSum + :rating, "
            + "s.reviewCount = s.reviewCount + 1, "
            + "s.averageRating = (s.ratingSum + :rating) / (s.reviewCount + 1) "
            + "where s.shopId = :shopId")
    int applyReviewRating(@Param("shopId") Long shopId, @Param("rating") BigDecimal rating);
}
