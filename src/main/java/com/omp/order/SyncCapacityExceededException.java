package com.omp.order;

/** 동기 주문의 동시 처리 상한을 넘어 거절했다. 비동기 거절과 같은 응답 코드(429, 설정으로 503) + Retry-After로 매핑된다. */
public class SyncCapacityExceededException extends RuntimeException {
    public SyncCapacityExceededException() {
        super("sync order capacity exceeded");
    }
}
