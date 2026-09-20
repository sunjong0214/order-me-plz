package com.omp.order.async;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 비동기 주문 작업의 상태 저장소.
 * 엔트리는 생성·갱신 시점부터 TTL 동안 유지된다(완료·실패 갱신 시 TTL 재시작). 처리 중 먼저 만료되면 완료·실패 갱신은 무시되고
 * 복구되지 않는다(Caffeine expireAfterWrite 동작, 실측 확인). 클라이언트가 202를 받고 SSE에 붙기 전에
 * 작업이 끝나더라도 폴링(GET /async/{uuid})으로 결과를 확인할 수 있어야 하기 때문이다.
 * 명시적 삭제는 접수 자체가 거절된 경우(풀 포화)에만 수행한다.
 */
@Component
public class AsyncOrderManager {
    private final Cache<String, OrderProcessingContext> map;

    public AsyncOrderManager(@Value("${omp.order.async.state-ttl:5m}") Duration stateTtl) {
        this.map = Caffeine.newBuilder()
                .expireAfterWrite(stateTtl)
                .build();
    }

    public void put(String key, OrderProcessingContext value) {
        map.asMap().putIfAbsent(key, value);
    }

    public void complete(String key, Long id) {
        map.asMap().computeIfPresent(key, (k, v) -> v.completed(id));   // 상태+orderId를 한 객체로 교체
    }

    public void fail(String key) {
        map.asMap().computeIfPresent(key, (k, v) -> v.failed());
    }

    public void remove(String key) {
        map.invalidate(key);
    }

    public OrderJobState getJobState(String key) {
        return get(key).getOrderJobState();
    }

    public OrderIdentifier getOrderIdentifier(String key) {
        return get(key).getOrderIdentifier();
    }

    public OrderProcessingContext get(String key) {
        OrderProcessingContext info = map.getIfPresent(key);
        if (info == null) {
            throw new OrderJobNotFoundException(key);
        }
        return info;
    }
}
