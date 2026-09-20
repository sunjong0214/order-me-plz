package com.omp.order.async;

/** 접수된 주문 작업 하나의 불변 컨텍스트. 상태 전이는 새 인스턴스로 교체한다. */
public record OrderProcessingContext(OrderIdentifier orderIdentifier, OrderJobState orderJobState) {

    public OrderProcessingContext(OrderIdentifier orderIdentifier) {
        this(orderIdentifier, OrderJobState.processing());
    }

    public OrderProcessingContext completed(Long orderId) {
        return new OrderProcessingContext(orderIdentifier, OrderJobState.completed(orderId));
    }

    public OrderProcessingContext failed() {
        return new OrderProcessingContext(orderIdentifier, OrderJobState.failed());
    }

    public OrderJobState getOrderJobState() {
        return orderJobState;
    }

    public OrderIdentifier getOrderIdentifier() {
        return orderIdentifier;
    }
}
