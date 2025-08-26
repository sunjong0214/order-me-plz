package com.omp.review.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateReviewEvent {
    private final Long reviewId;
    private final Long shopId;
    private final BigDecimal reviewRating;
}
