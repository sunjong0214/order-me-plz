package com.omp.order.async;

import com.omp.config.LatencyBuckets;
import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.SseEmitterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 접수된 주문의 저장 작업을 insertTaskExecutor에 제출한다.
 *
 * 왜 @TransactionalEventListener(AFTER_COMMIT)가 아닌 직접 호출인가:
 * Spring은 AFTER_COMMIT 리스너를 afterCompletion 단계에서 실행하고 그곳의 예외를 로그로만 남긴다(호출자로 전파 안 됨).
 * 이벤트 방식이면 제출이 거절돼도 컨트롤러는 202를 반환한다(AsyncOrderRejectionTest로 확인).
 * 접수 트랜잭션은 검증 SELECT만 수행하므로 "커밋 후"를 기다릴 데이터도 없다. 따라서 서비스가 직접 호출하고
 * 거절 예외를 요청 스레드로 전파해 429로 응답한다.
 *
 * 대기 자리는 호출자가 미리 예약한다(AsyncOrderAdmission). 제출에 성공하면 자리를 워커에 넘기고, 워커가 작업을 시작할 때 반납한다.
 *
 * 계측: 제출 시각을 기준으로 큐 대기(워커 시작까지)와 저장 완료(커밋까지)를 Timer로 기록한다.
 * 사용자가 체감하는 확정 시간 ≈ 접수 응답 시간(k6) + 저장 완료 시간(여기).
 */
@Component
@Slf4j
public class AsyncOrderHandler {
    /** 워커가 할 일. SPLIT은 접수에서 검증을 마쳤으므로 INSERT만, SINGLE은 워커 트랜잭션에서 검증 + INSERT. */
    public enum WorkerTask { INSERT_ONLY, VALIDATE_AND_INSERT }

    private final AsyncOrderProcessor asyncOrderProcessor;
    private final AsyncOrderManager asyncOrderManager;
    private final AsyncOrderAdmission asyncOrderAdmission;
    private final SseEmitterService sseEmitterService;
    private final Executor insertTaskExecutor;
    private final Counter failed;
    private final Timer queueWait;
    private final Timer completedLatency;
    private final Timer failedLatency;

    public AsyncOrderHandler(AsyncOrderProcessor asyncOrderProcessor,
                             AsyncOrderManager asyncOrderManager,
                             AsyncOrderAdmission asyncOrderAdmission,
                             SseEmitterService sseEmitterService,
                             Executor insertTaskExecutor,
                             MeterRegistry meterRegistry) {
        this.asyncOrderProcessor = asyncOrderProcessor;
        this.asyncOrderManager = asyncOrderManager;
        this.asyncOrderAdmission = asyncOrderAdmission;
        this.sseEmitterService = sseEmitterService;
        this.insertTaskExecutor = insertTaskExecutor;
        this.failed = Counter.builder("omp.order.async.failed")
                .description("접수 후 워커 단계에서 실패한 주문 수 (SINGLE 구성의 검증 실패 포함)")
                .register(meterRegistry);
        this.queueWait = LatencyBuckets.timer("omp.order.async.queue.wait",
                        "제출부터 워커가 작업을 시작할 때까지의 큐 대기 시간")
                .register(meterRegistry);
        this.completedLatency = LatencyBuckets.timer("omp.order.async.completion",
                        "제출부터 저장 트랜잭션 커밋까지 걸린 시간")
                .tag("outcome", "completed")
                .register(meterRegistry);
        this.failedLatency = LatencyBuckets.timer("omp.order.async.completion",
                        "제출부터 저장 트랜잭션 커밋까지 걸린 시간")
                .tag("outcome", "failed")
                .register(meterRegistry);
    }

    /**
     * 저장 작업을 제출한다. 예약한 자리 덕분에 큐가 넘치지 않으므로 executor 거절은 정상이라면 일어나지 않는다.
     * 일어나면(안전망) 상태 엔트리를 지우고 AsyncCapacityExceededException(→ 429)을 던지며, 자리는 호출자가 close()로 반납한다.
     */
    public void submit(CreateAsyncOrderEvent event, AsyncOrderAdmission.Slot slot, WorkerTask task) {
        long submittedAt = System.nanoTime();
        CompletableFuture<Long> future;
        try {
            future = CompletableFuture.supplyAsync(() -> {
                slot.release();   // 워커가 작업을 꺼냈다: 큐 한 칸이 비었다
                queueWait.record(System.nanoTime() - submittedAt, TimeUnit.NANOSECONDS);
                // REQUIRES_NEW: 반환 시점에 커밋 완료
                return task == WorkerTask.INSERT_ONLY
                        ? asyncOrderProcessor.processOrderTask(event)
                        : asyncOrderProcessor.validateAndProcessOrderTask(event);
            }, insertTaskExecutor);
        } catch (RejectedExecutionException e) {
            asyncOrderManager.remove(event.getUuid());
            log.debug("order accept rejected by executor : {}", event.getUuid());   // 건별 WARN은 과부하에서 로그 폭주가 된다
            throw asyncOrderAdmission.reject();
        }
        slot.handOff();

        future.whenComplete((orderId, orderException) -> {
            long elapsed = System.nanoTime() - submittedAt;
            if (orderException == null) {
                completedLatency.record(elapsed, TimeUnit.NANOSECONDS);
                asyncOrderManager.complete(event.getUuid(), orderId);
                sseEmitterService.orderCreateComplete(event.getUuid());
            } else {
                failedLatency.record(elapsed, TimeUnit.NANOSECONDS);
                failed.increment();
                asyncOrderManager.fail(event.getUuid());
                sseEmitterService.orderCreateFail(event.getUuid());
                log.warn("order create fail : {}", orderException.getMessage());
            }
        });
    }
}
