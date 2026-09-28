package com.omp.order.async;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 비동기 주문 설정.
 *
 * transaction — 트랜잭션 구성.
 * SINGLE (채택): 접수는 DB를 쓰지 않고 202, 워커 트랜잭션 하나에서 검증 SELECT + INSERT. 주문당 트랜잭션 1개(동기와 같은 DB 작업).
 *               잘못된 주문도 202를 받고, 이후 상태 조회·SSE에서 FAILED가 된다.
 * SPLIT (비교군): 접수 트랜잭션에서 검증 SELECT → 202, 워커 트랜잭션에서 INSERT. 주문당 트랜잭션 2개.
 *               잘못된 주문은 요청 즉시 400이지만, 검증과 저장 사이(큐 대기, 최대 약 Q ÷ W초)에 바뀐 상태는 다시 검증하지 않는다.
 * 선택 근거(파일럿 P2, 2026-09-27): 같은 워커 수에서 SINGLE의 저장 처리량이 16~18% 높고 접수 응답이 빠르며,
 * 주문당 MySQL 명령이 15 → 9개로 준다. 관심사 분리는 코드 구조(OrderValidator·AsyncOrderAdmission·AsyncOrderHandler·
 * AsyncOrderProcessor)로 유지하고 트랜잭션 경계만 저장 단계 하나로 둔다. 근거와 수치는 benchmark/README.md 2절.
 *
 * rejectStatus — 대기 자리가 없을 때의 응답 코드. 429(기본) 또는 503.
 * Tomcat은 503 응답 뒤 연결을 닫고("Connection: close") 429는 연결을 유지한다. 거절이 많은 과부하에서 503은 거절마다
 * 새 TCP 연결을 만들게 해, 4,000건/s에서 초당 새 연결 약 44 → 800~1,050개, 재전송 약 15배로 서버가 무너졌다(3회 중 3회).
 * 429는 같은 부하에서 무너지지 않았다(benchmark/results/2026-09-26-P2 7절). 앞에 프록시(Nginx)를 두면 프록시가
 * 클라이언트 연결을 쥐므로 앱은 429를 그대로 두고, 클라이언트에 보낼 코드(503 등)는 프록시에서 바꾼다(benchmark/README.md 2절).
 */
@ConfigurationProperties(prefix = "omp.order.async")
public record AsyncOrderProperties(@DefaultValue("single") Transaction transaction,
                                   @DefaultValue("429") int rejectStatus) {

    public enum Transaction { SPLIT, SINGLE }

    public AsyncOrderProperties {
        if (rejectStatus != 503 && rejectStatus != 429) {
            throw new IllegalArgumentException("omp.order.async.reject-status 는 429 또는 503 : " + rejectStatus);
        }
    }

    public boolean isSplit() {
        return transaction == Transaction.SPLIT;
    }
}
