package com.omp.order.async;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
@Slf4j
public class AsyncOrderHandler {
    private final AsyncOrderProcessor asyncOrderProcessor;
    private final AsyncOrderManager asyncOrderManager;
    private final OrderCreateWebhook webhook;
    private final Executor insertTaskExecutor;

    @TransactionalEventListener
    public void asyncCreateOrder(CreateAsyncOrderEvent event) {
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return asyncOrderProcessor.processOrderTask(event);
                    } catch (Exception e) {
                        throw new IllegalStateException("Order inset fail", e);
                    }
                }, insertTaskExecutor)
                .whenComplete((orderId, orderException) -> {
                    if (orderException == null) {
                        asyncOrderManager.complete(event.getUuid(), orderId);
                        webhook.sendOrderWebhook(
                                new OrderWebhookRequest(event.getUuid(), OrderJobStatus.COMPLETED, orderId));
                    } else {
                        asyncOrderManager.fail(event.getUuid());
                        webhook.sendFailedOrderWebhook(event, orderException);
                        log.warn("order create fail : {}", orderException.getMessage());
                    }
                });
    }
}
