package com.omp.promotion;

import com.omp.order.async.CodedOrderFailure;

/**
 * 할인 주문을 받지 않은 이유. 동기·비동기 접수에서는 409(NOT_FOUND는 404) + 코드, 비동기 워커에서는 FAILED + 코드가 된다.
 * 매진은 이벤트 중 초당 수천 건 나오는 정상 결과라 스택 트레이스를 만들지 않는다.
 */
public class PromotionRejectedException extends RuntimeException implements CodedOrderFailure {

    public enum Reason { SOLD_OUT, ALREADY_JOINED, NOT_OPEN, NOT_FOUND }

    private final Reason reason;

    public PromotionRejectedException(Reason reason) {
        super(reason.name(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    @Override
    public String failureCode() {
        return reason.name();
    }
}
