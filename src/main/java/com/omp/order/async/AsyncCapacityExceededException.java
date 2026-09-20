package com.omp.order.async;

/** 비동기 주문 풀(insertTaskExecutor)이 포화되어 접수를 거절했다. HTTP 503 + Retry-After로 매핑된다. */
public class AsyncCapacityExceededException extends RuntimeException {
    public AsyncCapacityExceededException(String uuid) {
        super("async order capacity exceeded : " + uuid);
    }
}
