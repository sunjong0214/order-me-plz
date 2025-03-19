package com.omp.review;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import com.omp.shop.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "REVIEWS")
@NoArgsConstructor(access = PROTECTED)
public class Review {
    @Getter
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "review_id")
    private Long id;
    @NotBlank
    private String title;
    @Positive
    private Long writerId;
    @NotBlank
    private String detail;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;
    @Positive
    @Getter
    private BigDecimal rating;

    public Review(final String title, final Long writerId, final String detail,
                  final BigDecimal rating, final Shop shop) {
        this.title = title;
        this.detail = detail;
        this.rating = rating;
        this.shop = shop;
    }
}
