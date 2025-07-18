package com.omp.order.async;

public class OrderJobState {
    private volatile OrderJobStatus status;
    private volatile Long orderId;

    public OrderJobState () {
        status = OrderJobStatus.PROCESSING;
    }

    public void complete() {
        status = OrderJobStatus.COMPLETED;
    }

    public void fail() {
        status = OrderJobStatus.FAILED;
    }

    public OrderJobStatus status() {
        return status;
    }

    public Long orderId() {
        return orderId;
    }

    public void setOrderId(Long id) {
        this.orderId = id;
    }
}
