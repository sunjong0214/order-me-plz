package com.omp.order.async;

/** 비동기 주문의 대기 자리가 없어 접수를 거절했다. HTTP 429(설정으로 503) + Retry-After로 매핑된다. */
public class AsyncCapacityExceededException extends RuntimeException {
    public AsyncCapacityExceededException() {
        super("async order capacity exceeded");
    }
}
