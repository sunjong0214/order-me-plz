package com.omp.order.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.omp.order.OrderSseEmitterMap;
import com.omp.order.SseEmitterService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 202 반환 후 클라이언트가 SSE에 붙기 전에 주문이 완료되어도 상태는 조회 가능해야 한다.
 * 기존 코드는 SSE 미연결 상태에서 완료 알림이 오면 상태 엔트리를 즉시 삭제해 폴링 GET까지 실패시켰다.
 */
class AsyncOrderStateRetentionTest {

    private final AsyncOrderManager manager = new AsyncOrderManager(Duration.ofMinutes(5));
    private final SseEmitterService sse = new SseEmitterService(new OrderSseEmitterMap(), manager, Runnable::run);

    @Test
    void SSE_미연결_상태에서_완료되어도_상태가_남아있다() {
        String uuid = "job-1";
        manager.put(uuid, new OrderProcessingContext(new OrderIdentifier(1L, 1L, 1L)));

        manager.complete(uuid, 42L);
        sse.orderCreateComplete(uuid);   // 아직 emitter 없음

        OrderJobState state = manager.getJobState(uuid);
        assertThat(state.status()).isEqualTo(OrderJobStatus.COMPLETED);
        assertThat(state.orderId()).isEqualTo(42L);
    }

    @Test
    void SSE_미연결_상태에서_실패해도_상태가_남아있다() {
        String uuid = "job-2";
        manager.put(uuid, new OrderProcessingContext(new OrderIdentifier(1L, 1L, 1L)));

        manager.fail(uuid);
        sse.orderCreateFail(uuid);

        assertThat(manager.getJobState(uuid).status()).isEqualTo(OrderJobStatus.FAILED);
    }

    @Test
    void 모르는_uuid는_전용_예외를_던진다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.getJobState("nope"))
                .isInstanceOf(OrderJobNotFoundException.class);
    }
}
