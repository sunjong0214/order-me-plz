package com.omp.review;

import com.omp.review.dto.CreateReviewEvent;
import com.omp.shop.Shop;
import com.omp.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewUpdateService {
    private final ShopRepository shopRepository;

    @Retryable(
            recover = "recoverSaveReviewBy",
            retryFor = {OptimisticLockingFailureException.class},
            backoff = @Backoff(
                    multiplier = 2.0,
                    random = true,
                    maxDelay = 6000
            )
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async
    @TransactionalEventListener
    public void updateAverageRating(final CreateReviewEvent event) {
        Shop shop = shopRepository.findByIdWithOptimisticLock(event.getShopId()).orElseThrow();
        shop.updateRatingAverage(event.getReviewRating());
    }

    @Recover
    public void recoverSaveReviewBy(final OptimisticLockingFailureException e, final CreateReviewEvent event) {
        log.error("review rating update fail : {}, {}", event.getReviewId(), e.getMessage());
    }
}
