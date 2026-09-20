package com.omp.order;

/** 주문 검증 실패(주문자 없음·차단, 가게 없음·영업 종료, 장바구니 없음). 동기·비동기 경로 공통. HTTP 400. */
public class InvalidOrderException extends RuntimeException {
    public InvalidOrderException(Long ordererId, Long shopId, Long cartId) {
        super("invalid order : ordererId=" + ordererId + ", shopId=" + shopId + ", cartId=" + cartId);
    }
}
