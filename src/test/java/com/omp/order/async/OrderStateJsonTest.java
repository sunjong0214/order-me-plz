package com.omp.order.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 폴링 응답과 SSE data로 나가는 상태 객체의 JSON 계약. 기존 가변 클래스는 getter가 없어 직렬화가 실패했다. */
class OrderStateJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 상태는_status와_orderId로_직렬화된다() throws Exception {
        String json = mapper.writeValueAsString(OrderJobState.completed(42L));
        assertThat(json).contains("\"status\":\"COMPLETED\"").contains("\"orderId\":42");
    }

    @Test
    void 식별자는_세_필드로_직렬화된다() throws Exception {
        String json = mapper.writeValueAsString(new OrderIdentifier(1L, 2L, 3L));
        assertThat(json).contains("\"ordererId\":1").contains("\"shopId\":2").contains("\"cartId\":3");
    }

    @Test
    void 완료_전이는_상태와_orderId를_한_객체로_바꾼다() {
        OrderProcessingContext before = new OrderProcessingContext(new OrderIdentifier(1L, 1L, 1L));
        OrderProcessingContext after = before.completed(7L);

        assertThat(before.getOrderJobState().status()).isEqualTo(OrderJobStatus.PROCESSING);
        assertThat(after.getOrderJobState()).isEqualTo(OrderJobState.completed(7L));
    }
}
