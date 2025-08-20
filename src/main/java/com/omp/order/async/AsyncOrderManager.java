package com.omp.order.async;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AsyncOrderManager {
    private final ConcurrentHashMap<String, OrderProcessingContext> map = new ConcurrentHashMap<>();

    public void put(String key, OrderProcessingContext value) {
        map.putIfAbsent(key, value);
    }

    public void complete(String key, Long id) {
        map.computeIfPresent(key, (k, v) -> {
            v.jobComplete();
            v.getOrderJobState().setOrderId(id);
            return v;
        });
    }

    public void fail(String key) {
        map.computeIfPresent(key, (k, v) -> {
            v.jobFail();
            return v;
        });
    }

    public void remove(String key) {
        map.remove(key);
    }

    public OrderJobState getJobState(String key) {
        return get(key).getOrderJobState();
    }

    public OrderIdentifier getOrderIdentifier(String key) {
        return get(key).getOrderIdentifier();
    }

    public OrderProcessingContext get(String key) {
        OrderProcessingContext info = map.get(key);
        if (info == null) {
            throw new IllegalStateException();
        }
        return info;
    }
}
