package com.omp.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 동기 주문 설정.
 *
 * maxInFlight — 동시에 처리하는 동기 주문 수 상한(비교군 "동기 + 빠른 거절"). 0이면 끈다(기본, 원래 동기).
 * 상한을 넘는 요청은 커넥션을 기다리지 않고 즉시 거절한다(응답 코드는 비동기와 같은 omp.order.async.reject-status).
 * 값은 비동기 대기 자리(큐 = 처리량 × 허용 대기)와 같은 원리로 동기 처리량 × 허용 대기로 정한다(benchmark/README.md 2절).
 * Tomcat 요청 스레드 수(server.tomcat.threads.max)보다 작아야 의미가 있다. 스레드를 잡은 요청만 여기까지 오기 때문이다.
 */
@ConfigurationProperties(prefix = "omp.order.sync")
public record SyncOrderProperties(@DefaultValue("0") int maxInFlight) {

    public SyncOrderProperties {
        if (maxInFlight < 0) {
            throw new IllegalArgumentException("omp.order.sync.max-in-flight 는 0(끄기) 이상 : " + maxInFlight);
        }
    }

    public boolean isLimited() {
        return maxInFlight > 0;
    }
}
