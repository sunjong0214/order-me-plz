package com.omp.order.async;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderProcessingContext {
    private final OrderIdentifier orderIdentifier;
    private final OrderJobState orderJobState;

    public OrderProcessingContext(OrderIdentifier orderIdentifier) {
        this.orderIdentifier = orderIdentifier;
        this.orderJobState = new OrderJobState();
    }

    public OrderJobState jobFail() {
        orderJobState.fail();
        return orderJobState;
    }

    public OrderJobState jobComplete() {
        orderJobState.complete();
        return orderJobState;
    }

    public OrderJobState checkStatus() {
        return orderJobState;
    }

    public OrderJobState getOrderJobState() {
        return orderJobState;
    }

    public OrderIdentifier getOrderIdentifier() {
        return orderIdentifier;
    }
}
