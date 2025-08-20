package com.omp.order;

import com.omp.order.async.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseEmitterService {
    private final OrderSseEmitterMap orderSseEmitterMap;
    private final AsyncOrderManager asyncOrderManager;
    private final Executor sseTaskExecutor;

    public SseEmitter createOrderSse(final String orderUuid) {
        SseEmitter emitter = new SseEmitter(5L * 60000);

        CompletableFuture.runAsync(() -> {
                    orderSseEmitterMap.put(orderUuid, emitter);

                    emitter.onCompletion(() -> {
                        orderSseEmitterMap.remove(orderUuid);
                        asyncOrderManager.remove(orderUuid);
                    });
                    emitter.onTimeout(() -> {
                        log.warn("order emitter timeout : {}", orderUuid);
                        orderSseEmitterMap.remove(orderUuid);
                        asyncOrderManager.remove(orderUuid);
                    });
                    emitter.onError((e) -> {
                        log.error("order emitter error : {}, {}", orderUuid, e.getMessage());
                        orderSseEmitterMap.remove(orderUuid);
                        asyncOrderManager.remove(orderUuid);
                    });

                    sendOrderState(orderUuid);
                }, sseTaskExecutor)
                .whenComplete((v, e) -> {
                    // 스케줄러를 통해 실패한 전송 다시 ? 혹은 그냥 에러?
                    if (e != null) {
                        log.error("order emitter error : {}", orderUuid);
                        orderCreateFail(orderUuid);
                    }
                });

        return emitter;
    }

    private void sendOrderState(final String orderUuid) {
        CompletableFuture.supplyAsync(() -> {
                    OrderProcessingContext context = asyncOrderManager.get(orderUuid);
                    return orderSseEmitterMap.checkOrderStateThenSend(orderUuid, context);
                }, sseTaskExecutor)
                .whenComplete((emitter, e) -> {
                    if (e != null) {
                        log.error("order send fail : {}", e.getMessage());
                    } else {
                        log.info("order send success!");
                    }
                    if (emitter == null || e != null) {
                        asyncOrderManager.remove(orderUuid);
                    }
                });
    }

    public void orderCreateComplete(final String orderUuid) {
        CompletableFuture.runAsync(() -> {
                    SseEmitter sseEmitter = orderSseEmitterMap.get(orderUuid);
                    if (sseEmitter == null) {
                        log.warn("sse emitter is not connected : {}", orderUuid);
                        return;
                    }
                    sendOrderState(orderUuid);
                }, sseTaskExecutor)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.error("order send fail : {}", e.getMessage());
                    } else {
                        log.info("order send success!");
                    }
                    asyncOrderManager.remove(orderUuid);
                });
    }

    public void orderCreateFail(final String orderUuid) {
        CompletableFuture.runAsync(() -> {
                    SseEmitter sseEmitter = orderSseEmitterMap.get(orderUuid);
                    if (sseEmitter == null) {
                        log.warn("sse emitter is not connected : {}", orderUuid);
                        return;
                    }
                    OrderProcessingContext context = asyncOrderManager.get(orderUuid);
                    orderSseEmitterMap.sendFail(orderUuid, context);
                }, sseTaskExecutor)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.error("order send fail : {}", e.getMessage());
                    } else {
                        log.info("order send success!");
                    }
                    asyncOrderManager.remove(orderUuid);
                });
        ;
    }
}
