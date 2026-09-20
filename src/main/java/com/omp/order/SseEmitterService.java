package com.omp.order;

import com.omp.order.async.AsyncOrderManager;
import com.omp.order.async.OrderProcessingContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 주문 완료 알림 SSE.
 * 이 클래스는 emitter의 수명만 관리한다. 주문 상태(AsyncOrderManager)는 TTL로 만료되므로
 * SSE 연결 유무·전송 성공 여부에 따라 상태를 지우지 않는다. (지우면 SSE 연결 전 완료된 주문의 폴링이 실패한다.)
 */
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

                    emitter.onCompletion(() -> orderSseEmitterMap.remove(orderUuid));
                    emitter.onTimeout(() -> {
                        log.warn("order emitter timeout : {}", orderUuid);
                        orderSseEmitterMap.remove(orderUuid);
                    });
                    emitter.onError((e) -> {
                        log.error("order emitter error : {}, {}", orderUuid, e.getMessage());
                        orderSseEmitterMap.remove(orderUuid);
                    });

                    sendOrderState(orderUuid);
                }, sseTaskExecutor)
                .whenComplete((v, e) -> {
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
                    }
                });
    }

    public void orderCreateComplete(final String orderUuid) {
        CompletableFuture.runAsync(() -> {
                    SseEmitter sseEmitter = orderSseEmitterMap.get(orderUuid);
                    if (sseEmitter == null) {
                        return;   // 아직 SSE 미연결. 상태는 TTL 동안 유지되므로 연결 시 또는 폴링으로 확인 가능.
                    }
                    sendOrderState(orderUuid);
                }, sseTaskExecutor)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.error("order send fail : {}", e.getMessage());
                    }
                });
    }

    public void orderCreateFail(final String orderUuid) {
        CompletableFuture.runAsync(() -> {
                    SseEmitter sseEmitter = orderSseEmitterMap.get(orderUuid);
                    if (sseEmitter == null) {
                        return;
                    }
                    OrderProcessingContext context = asyncOrderManager.get(orderUuid);
                    orderSseEmitterMap.sendFail(orderUuid, context);
                }, sseTaskExecutor)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.error("order send fail : {}", e.getMessage());
                    }
                });
    }
}
