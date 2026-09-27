package com.omp.order.async;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 비동기 주문의 트랜잭션 구성. 처리량 비교(benchmark/README.md)를 위해 설정으로 전환한다.
 * SPLIT  (기본): 접수 트랜잭션에서 검증 SELECT → 202, 워커 트랜잭션에서 INSERT. 주문당 트랜잭션 2개.
 *               잘못된 주문(없는 사용자·가게·장바구니, 영업 종료, 차단)은 요청 즉시 400.
 * SINGLE       : 접수는 DB를 쓰지 않고 202, 워커 트랜잭션 하나에서 검증 SELECT + INSERT. 주문당 트랜잭션 1개(동기와 같은 DB 작업).
 *               잘못된 주문도 202를 받고, 이후 상태 조회·SSE에서 FAILED가 된다.
 */
@ConfigurationProperties(prefix = "omp.order.async")
public record AsyncOrderProperties(@DefaultValue("split") Transaction transaction) {

    public enum Transaction { SPLIT, SINGLE }

    public boolean isSplit() {
        return transaction == Transaction.SPLIT;
    }
}
