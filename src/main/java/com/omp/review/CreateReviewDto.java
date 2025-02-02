package com.omp.review;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateReviewDto {
    private final String title;
    private final Long writerId;
    private final Long shopId;
    private final String detail;
    private final Double rating;

    public static Review of(final CreateReviewDto dto) {
        return new Review(dto.title, dto.writerId, dto.shopId, dto.detail, dto.rating);
    }
}
