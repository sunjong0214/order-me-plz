package com.omp.order;

import com.omp.order.async.OrderJobStatus;
import com.omp.order.async.OrderProcessingContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderSseEmitterMap {
    private final Map<String, SseEmitter> map = new ConcurrentHashMap<>();

    public void put(final String uuid, final SseEmitter sseEmitter) {
        map.put(uuid, sseEmitter);
    }

    public void remove(final String orderUuid) {
        map.remove(orderUuid);
    }

    public SseEmitter get(String uuid) {
        return map.get(uuid);
    }

    public SseEmitter checkOrderStateThenSend(final String uuid, final OrderProcessingContext context) {
        return map.computeIfPresent(uuid, (key, emitter) -> {
            if (context.getOrderJobState().status() == OrderJobStatus.COMPLETED) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("orderCreateComplete")
                            .data(context.getOrderJobState()));
                    emitter.complete();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("orderCreateNotComplete")
                        .data(context.getOrderIdentifier()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return emitter;
        });
    }

    public void sendFail(String orderUuid, OrderProcessingContext context) {
        map.computeIfPresent(orderUuid, (key, emitter) -> {
            if (context.getOrderJobState().status() == OrderJobStatus.FAILED) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("orderCreateFail")
                            .data(context.getOrderIdentifier()));
                    emitter.complete();
                } catch (Exception e) {
                    throw new RuntimeException();
                }
            }
            return null;
        });
    }
}
