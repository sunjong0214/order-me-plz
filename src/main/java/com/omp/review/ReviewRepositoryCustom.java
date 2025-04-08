package com.omp.review;

import java.util.List;

public interface ReviewRepositoryCustom {
    List<ReviewDetail> getReviewDetails(Long shopId);
}
