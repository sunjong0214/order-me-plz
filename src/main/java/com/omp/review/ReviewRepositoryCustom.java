package com.omp.review;

import com.omp.review.dto.ReviewDetail;

import java.util.List;

public interface ReviewRepositoryCustom {
    List<ReviewDetail> getReviewDetails(Long shopId);
}
