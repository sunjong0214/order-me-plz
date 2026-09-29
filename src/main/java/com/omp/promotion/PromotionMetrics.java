package com.omp.promotion;

import com.omp.promotion.PromotionRejectedException.Reason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 할인 주문 지표. confirmed를 1초마다 읽으면 초당 확정 건수와 "재고가 모두 확정된 시각"이 나온다(설계 4.2).
 * 거절(rejected)은 이유별, 반납(released)은 반납 경로별로 센다.
 */
@Component
public class PromotionMetrics {
    private final MeterRegistry registry;
    private final Counter reserved;
    private final Counter confirmed;
    private final Counter constraintViolation;
    private final Map<Reason, Counter> rejected = new EnumMap<>(Reason.class);
    private final Map<String, Counter> released = new ConcurrentHashMap<>();
    private final TransactionSynchronization countOnCommit;

    public PromotionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.reserved = Counter.builder("omp.promotion.reserved")
                .description("메모리 번호표를 예약한 수").register(registry);
        this.confirmed = Counter.builder("omp.promotion.confirmed")
                .description("커밋까지 끝나 확정된 할인 주문 수").register(registry);
        this.constraintViolation = Counter.builder("omp.promotion.constraint_violation")
                .description("번호표 유니크·외래 키 위반 수. 발급기 버그 신호이며 0이어야 한다").register(registry);
        for (Reason r : Reason.values()) {
            rejected.put(r, Counter.builder("omp.promotion.rejected").tag("reason", r.name().toLowerCase())
                    .description("받지 않은 할인 주문 수").register(registry));
        }
        Counter c = confirmed;
        this.countOnCommit = new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                c.increment();
            }
        };
    }

    void reserved() {
        reserved.increment();
    }

    void constraintViolation() {
        constraintViolation.increment();
    }

    PromotionRejectedException rejected(Reason reason) {
        rejected.get(reason).increment();
        return new PromotionRejectedException(reason);
    }

    void released(String reason) {
        released.computeIfAbsent(reason, k -> Counter.builder("omp.promotion.released").tag("reason", k)
                .description("예약했다가 돌려준 번호표 수").register(registry)).increment();
    }

    /** 현재 트랜잭션이 커밋되면 확정 수를 올린다. 세 방식 모두 같은 자리에서 센다. */
    void countConfirmedOnCommit() {
        TransactionSynchronizationManager.registerSynchronization(countOnCommit);
    }
}
