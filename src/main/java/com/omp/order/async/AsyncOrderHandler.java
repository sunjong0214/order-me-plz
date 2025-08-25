package com.omp.order.async;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RequiredArgsConstructor
@Component
@Slf4j
public class AsyncOrderHandler {
    private final AsyncOrderProcessor asyncOrderProcessor;
    private final AsyncOrderManager asyncOrderManager;
    private final SseEmitterService sseEmitterService;
    private final Executor insertTaskExecutor;

    @TransactionalEventListener
    public void asyncCreateOrder(CreateAsyncOrderEvent event) {
        CompletableFuture
                .supplyAsync(() -> asyncOrderProcessor.processOrderTask(event), insertTaskExecutor)
                .whenComplete((orderId, orderException) -> {
                    if (orderException == null) {
                        asyncOrderManager.complete(event.getUuid(), orderId);
                        sseEmitterService.orderCreateComplete(event.getUuid());
                    } else {
                        asyncOrderManager.fail(event.getUuid());
                        sseEmitterService.orderCreateFail(event.getUuid());
                        log.warn("order create fail : {}", orderException.getMessage());
                    }
                });
    }
}
