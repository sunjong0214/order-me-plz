package com.omp.order.async;

/**
 * 주문 작업 상태 스냅샷. 불변이므로 status와 orderId가 항상 함께 관측되고, record라 JSON 직렬화 계약이 명확하다.
 * (기존 가변 객체는 status 변경과 orderId 대입이 두 단계라 COMPLETED + null orderId 조합이 노출될 수 있었고, getter가 없어 직렬화가 실패했다.)
 */
public record OrderJobState(OrderJobStatus status, Long orderId) {

    public static OrderJobState processing() {
        return new OrderJobState(OrderJobStatus.PROCESSING, null);
    }

    public static OrderJobState completed(Long orderId) {
        return new OrderJobState(OrderJobStatus.COMPLETED, orderId);
    }

    public static OrderJobState failed() {
        return new OrderJobState(OrderJobStatus.FAILED, null);
    }
}
