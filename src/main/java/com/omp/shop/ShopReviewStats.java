package com.omp.shop;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "SHOP_REVIEW_STATS")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class ShopReviewStats {
    @Id
    @Column(name = "shop_id")
    private Long shopId;
    @PositiveOrZero
    private long reviewCount;
    @PositiveOrZero
    private BigDecimal ratingSum;
    @PositiveOrZero
    private BigDecimal averageRating;

    public ShopReviewStats(final Long shopId) {
        this.shopId = shopId;
        this.reviewCount = 0L;
        this.ratingSum = BigDecimal.ZERO;
        this.averageRating = BigDecimal.ZERO;
    }
}
