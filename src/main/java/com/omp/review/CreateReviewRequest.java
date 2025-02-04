package com.omp.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateReviewRequest {
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

    public static CreateReviewDto of(final CreateReviewRequest request) {
        return new CreateReviewDto(request.title, request.writerId, request.shopId, request.detail, request.rating);
    }
}
