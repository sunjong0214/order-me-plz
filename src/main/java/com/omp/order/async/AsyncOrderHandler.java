package com.omp.order.async;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.SseEmitterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 접수된 주문의 INSERT 작업을 insertTaskExecutor에 제출한다.
 *
 * 왜 @TransactionalEventListener(AFTER_COMMIT)가 아닌 직접 호출인가:
 * Spring은 AFTER_COMMIT 리스너를 afterCompletion 단계에서 실행하고 그곳의 예외를 로그로만 남긴다(호출자로 전파 안 됨).
 * 이벤트 방식이면 풀 포화로 제출이 거절돼도 컨트롤러는 202를 반환한다(AsyncOrderRejectionTest로 확인).
 * 접수 트랜잭션은 검증 SELECT만 수행하므로 "커밋 후"를 기다릴 데이터도 없다. 따라서 서비스가 직접 호출하고
 * 거절 예외를 요청 스레드로 전파해 503으로 응답한다.
 */
@Component
@Slf4j
public class AsyncOrderHandler {
    private final AsyncOrderProcessor asyncOrderProcessor;
    private final AsyncOrderManager asyncOrderManager;
    private final SseEmitterService sseEmitterService;
    private final Executor insertTaskExecutor;
    private final Counter rejected;

    public AsyncOrderHandler(AsyncOrderProcessor asyncOrderProcessor,
                             AsyncOrderManager asyncOrderManager,
                             SseEmitterService sseEmitterService,
                             Executor insertTaskExecutor,
                             MeterRegistry meterRegistry) {
        this.asyncOrderProcessor = asyncOrderProcessor;
        this.asyncOrderManager = asyncOrderManager;
        this.sseEmitterService = sseEmitterService;
        this.insertTaskExecutor = insertTaskExecutor;
        this.rejected = Counter.builder("omp.order.async.rejected")
                .description("insertTaskExecutor 포화로 접수를 거절한 주문 수")
                .register(meterRegistry);
    }

    /** 풀이 포화되어 거절되면 상태 엔트리를 지우고 AsyncCapacityExceededException(→ 503)을 던진다. */
    public void submit(CreateAsyncOrderEvent event) {
        CompletableFuture<Long> future;
        try {
            future = CompletableFuture.supplyAsync(() -> asyncOrderProcessor.processOrderTask(event), insertTaskExecutor);
        } catch (RejectedExecutionException e) {
            rejected.increment();
            asyncOrderManager.remove(event.getUuid());
            log.warn("order accept rejected (executor saturated) : {}", event.getUuid());
            throw new AsyncCapacityExceededException(event.getUuid());
        }

        future.whenComplete((orderId, orderException) -> {
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
