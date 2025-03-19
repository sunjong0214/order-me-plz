package com.omp.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateReviewRequest {
    @NotBlank
    private final String title;
    @Positive
    private final Long writerId;
    @Positive
    @Getter
    private final Long shopId;
    @NotBlank
    private final String detail;
    @Positive
    private final BigDecimal rating;

    public static CreateReviewDto from(final CreateReviewRequest request) {
        return new CreateReviewDto(request.title, request.writerId, request.shopId, request.detail, request.rating);
    }
}
