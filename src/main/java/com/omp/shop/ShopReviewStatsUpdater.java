package com.omp.shop;

import com.omp.review.dto.CreateReviewEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ASYNC 모드(비교군, 기본은 SYNC): 리뷰 커밋 후 통계 UPDATE를 reviewStatsExecutor에 제출한다.
 * 풀이 포화되면 몰래 인라인 실행하지 않고 거절을 기록한다(reviewId 로그 + 카운터).
 * 리뷰는 이미 커밋됐으므로 요청은 성공으로 끝나고, 통계 누락은 카운터로 관측된다.
 */
@Component
@Slf4j
public class ShopReviewStatsUpdater {
    private final ShopReviewStatsWriter writer;
    private final Executor reviewStatsExecutor;
    private final Counter rejected;
    private final Counter failed;

    public ShopReviewStatsUpdater(ShopReviewStatsWriter writer,
                                  @Qualifier("reviewStatsExecutor") Executor reviewStatsExecutor,
                                  MeterRegistry meterRegistry) {
        this.writer = writer;
        this.reviewStatsExecutor = reviewStatsExecutor;
        this.rejected = Counter.builder("omp.review.stats.rejected")
                .description("reviewStatsExecutor 포화로 통계 갱신이 거절된 리뷰 수")
                .register(meterRegistry);
        this.failed = Counter.builder("omp.review.stats.failed")
                .description("통계 UPDATE 실행 중 예외가 난 리뷰 수")
                .register(meterRegistry);
    }

    @TransactionalEventListener
    public void onReviewCreated(final CreateReviewEvent event) {
        try {
            reviewStatsExecutor.execute(() -> update(event));
        } catch (RejectedExecutionException e) {
            rejected.increment();
            log.error("review stats update rejected (executor saturated) : reviewId={}, shopId={}",
                    event.getReviewId(), event.getShopId());
        }
    }

    private void update(CreateReviewEvent event) {
        try {
            writer.apply(event.getReviewId(), event.getShopId(), event.getReviewRating());
        } catch (Exception e) {
            failed.increment();
            log.error("review stats update fail : reviewId={}, shopId={}, cause={}",
                    event.getReviewId(), event.getShopId(), e.getMessage());
        }
    }
}
