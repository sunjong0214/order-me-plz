package com.omp.shop;

/** 가게의 통계 행이 없어 UPDATE 영향 행이 0건. 데이터 불일치이므로 조용히 넘기지 않는다. */
public class ShopReviewStatsMissingException extends RuntimeException {
    public ShopReviewStatsMissingException(Long reviewId, Long shopId) {
        super("review stats row missing : reviewId=" + reviewId + ", shopId=" + shopId);
    }
}
