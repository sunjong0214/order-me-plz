package com.omp.shop;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "SHOPS")
@NoArgsConstructor(access = PROTECTED)
public class Shop {
    public static final double DIVISOR = 10.0;
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "shop_id")
    @Getter
    private Long id;
    @NotBlank
    private String name;
    @Enumerated(STRING)
    private ShopCategory category;
    @NotNull
    private boolean isOpen;
    @Version
    private int version;
    @PositiveOrZero
    private BigDecimal averageRating;
    @PositiveOrZero
    private int reviewCount;

    public Shop(String name, ShopCategory category) {
        this.name = name;
        this.category = category;
        this.isOpen = false;
        this.averageRating = BigDecimal.ZERO;
        this.reviewCount = 0;
    }

    public void updateRatingAverage(final BigDecimal newRating) {
        this.averageRating = ((averageRating.multiply(BigDecimal.valueOf(reviewCount)).add(newRating))
                .divide(BigDecimal.valueOf(reviewCount + 1), 5, RoundingMode.HALF_UP));
        this.reviewCount++;
    }

    public double getRatingAverage() {
        return Math.round(averageRating.doubleValue() * DIVISOR) / DIVISOR;
    }

    public BigDecimal getRawRatingAverage() {
        return averageRating;
    }

    public boolean isOpen() {
        return isOpen;
    }

//    public void addReview(Review review) {
//        this.ratingAverage = shopReviews.addReviewAndCalculateNewAverage(review, ratingAverage);
//        review.setShop(this);
//    }
}
