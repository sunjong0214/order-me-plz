package com.omp.order.async;

/** 알 수 없는 주문 작업 식별자(만료 또는 미접수). HTTP 404로 매핑된다. */
public class OrderJobNotFoundException extends RuntimeException {
    public OrderJobNotFoundException(String uuid) {
        super("order job not found : " + uuid);
    }
}
