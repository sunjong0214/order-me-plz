package com.omp.shop;

import com.omp.review.dto.CreateReviewEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopReviewStatsUpdater {
    private final ShopReviewStatsRepository shopReviewStatsRepository;

    @Async("reviewStatsExecutor")
    @Transactional
    @TransactionalEventListener
    public void updateReviewStats(final CreateReviewEvent event) {
        try {
            int updated = shopReviewStatsRepository.applyReviewRating(event.getShopId(), event.getReviewRating());
            if (updated == 0) {
                log.error("review stats row missing : reviewId={}, shopId={}",
                        event.getReviewId(), event.getShopId());
            }
        } catch (Exception e) {
            log.error("review stats update fail : reviewId={}, shopId={}, cause={}",
                    event.getReviewId(), event.getShopId(), e.getMessage());
        }
    }
}
