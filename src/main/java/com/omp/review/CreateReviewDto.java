package com.omp.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
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
    private final Double rating;

    public static Review of(final CreateReviewDto dto) {
        return new Review(dto.title, dto.writerId, dto.shopId, dto.detail, dto.rating);
    }
}
