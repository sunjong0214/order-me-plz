package com.omp.order.async;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 주문 작업 상태 스냅샷. 불변이므로 status와 orderId가 항상 함께 관측되고, record라 JSON 직렬화 계약이 명확하다.
 * (기존 가변 객체는 status 변경과 orderId 대입이 두 단계라 COMPLETED + null orderId 조합이 노출될 수 있었고, getter가 없어 직렬화가 실패했다.)
 */
public record OrderJobState(OrderJobStatus status,
                            Long orderId,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String failureCode) {

    public static OrderJobState processing() {
        return new OrderJobState(OrderJobStatus.PROCESSING, null, null);
    }

    public static OrderJobState completed(Long orderId) {
        return new OrderJobState(OrderJobStatus.COMPLETED, orderId, null);
    }

    public static OrderJobState failed() {
        return failed(null);
    }

    /** failureCode: 사용자에게 알려 줄 실패 이유(예: 할인 매진 SOLD_OUT). 이유가 없는 실패는 null이고 JSON에 나가지 않는다. */
    public static OrderJobState failed(String failureCode) {
        return new OrderJobState(OrderJobStatus.FAILED, null, failureCode);
    }
}
