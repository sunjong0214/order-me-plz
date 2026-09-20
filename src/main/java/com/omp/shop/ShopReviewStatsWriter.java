package com.omp.shop;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 UPDATE 실행 단위. SYNC 모드에서는 리뷰 트랜잭션에 참여하고(REQUIRED),
 * ASYNC 모드에서는 워커 스레드에 트랜잭션이 없으므로 새 트랜잭션이 열린다.
 *
 * 영향 행 0건은 예외다. 모드별 의미:
 *  - SYNC : 예외가 리뷰 트랜잭션까지 롤백시킨다 → "리뷰와 통계를 같은 트랜잭션에서 일치"가 실제로 보장된다.
 *  - ASYNC: 리뷰는 이미 커밋됨. 호출자(ShopReviewStatsUpdater)가 잡아 실패 카운터·로그로 기록한다.
 */
@Component
@RequiredArgsConstructor
public class ShopReviewStatsWriter {
    private final ShopReviewStatsRepository shopReviewStatsRepository;

    @Transactional
    public void apply(Long reviewId, Long shopId, BigDecimal rating) {
        int updated = shopReviewStatsRepository.applyReviewRating(shopId, rating);
        if (updated == 0) {
            throw new ShopReviewStatsMissingException(reviewId, shopId);
        }
    }
}
