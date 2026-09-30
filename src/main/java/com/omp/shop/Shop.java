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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [bench/review-before] 개선 전 설계 재구성: 리뷰 통계를 shops 행에 둔다.
 * 리뷰 INSERT의 FK 검사가 이 행에 S락을 잡은 상태에서, 같은 트랜잭션의 통계 UPDATE가 같은 행에 X락을 요구한다.
 * main과의 차이는 이 통계 필드 3개와 ReviewService.saveReviewBy 만이다 (스레드 풀·검증·예외 처리·설정은 main과 동일).
 * 기존 행에 ddl-auto=update 로 컬럼이 추가되면 DECIMAL 컬럼은 NULL 이므로 회차 전 benchmark/sql/reset-round.sql 하단 UPDATE 로 0 초기화한다.
 */
@Entity
@Table(name = "SHOPS")
@NoArgsConstructor(access = PROTECTED)
public class Shop {
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
    @PositiveOrZero
    private long reviewCount;
    @PositiveOrZero
    private BigDecimal ratingSum;
    @PositiveOrZero
    private BigDecimal averageRating;

    public Shop(String name, ShopCategory category) {
        this.name = name;
        this.category = category;
        this.isOpen = false;
        this.reviewCount = 0L;
        this.ratingSum = BigDecimal.ZERO;
        this.averageRating = BigDecimal.ZERO;
    }

    /**
     * 읽고-계산-쓰기. 더티 체킹으로 flush 시 UPDATE shops ... WHERE shop_id=? 가 실행되어 X락을 요구한다.
     * 같은 가게 요청이 겹치면 (a) 둘 다 INSERT 로 S락을 든 뒤 X락을 기다려 데드락 → 한쪽 롤백(500),
     * (b) 한쪽이 먼저 커밋한 뒤 다른 쪽이 stale 값으로 덮어써 통계 유실. 둘 다 구 설계의 결함이며 verify.sql 로 구분해 관측한다.
     */
    public void applyReviewRating(final BigDecimal rating) {
        this.reviewCount++;
        this.ratingSum = this.ratingSum.add(rating);
        this.averageRating = this.ratingSum.divide(BigDecimal.valueOf(this.reviewCount), 2, RoundingMode.HALF_UP);
    }

    public boolean isOpen() {
        return isOpen;
    }
}
