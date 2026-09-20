package com.omp.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.omp.order.async.OrderIdentifier;
import com.omp.order.async.OrderProcessingContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 클라이언트가 작업 종료 뒤 늦게 SSE에 연결한 경우, 연결 시점 상태를 보고 종료 이벤트를 보내고 emitter를 닫아야 한다.
 * 기존 코드는 COMPLETED만 처리하고 FAILED는 "진행 중" 이벤트를 보낸 채 연결을 유지했다.
 */
class OrderSseEmitterMapTest {
    private final OrderSseEmitterMap map = new OrderSseEmitterMap();
    private final OrderProcessingContext processing = new OrderProcessingContext(new OrderIdentifier(1L, 1L, 1L));

    @Test
    void 늦게_연결했는데_이미_완료면_완료_이벤트를_보내고_emitter를_닫는다() {
        map.put("c", new SseEmitter());
        SseEmitter kept = map.checkOrderStateThenSend("c", processing.completed(9L));

        assertThat(kept).isNull();
        assertThat(map.contains("c")).isFalse();
    }

    @Test
    void 늦게_연결했는데_이미_실패면_실패_이벤트를_보내고_emitter를_닫는다() {
        map.put("f", new SseEmitter());
        SseEmitter kept = map.checkOrderStateThenSend("f", processing.failed());

        assertThat(kept).isNull();
        assertThat(map.contains("f")).isFalse();
    }

    @Test
    void 진행_중이면_emitter를_유지한다() {
        SseEmitter emitter = new SseEmitter();
        map.put("p", emitter);
        SseEmitter kept = map.checkOrderStateThenSend("p", processing);

        assertThat(kept).isSameAs(emitter);
        assertThat(map.contains("p")).isTrue();
    }
}
