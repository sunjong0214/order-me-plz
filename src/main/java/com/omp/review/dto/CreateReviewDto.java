package com.omp.review.dto;

import com.omp.review.Review;
import com.omp.shop.Shop;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateReviewDto {
    @NotBlank
    private final String title;
    @Positive
    private final Long writerId;
    @Positive
    private final Long shopId;
    @NotBlank
    private final String detail;
    @Positive
    private final BigDecimal rating;

    public static Review from(final CreateReviewDto dto, final Shop shop) {
        return new Review(dto.title, dto.writerId, dto.detail, dto.rating, shop);
    }
}
