package com.omp.order;

import com.omp.order.async.OrderJobStatus;
import com.omp.order.async.OrderProcessingContext;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    public boolean contains(String uuid) {
        return map.containsKey(uuid);
    }

    /**
     * 현재 상태를 전송한다. 종료 상태(COMPLETED/FAILED)면 해당 이벤트를 보내고 emitter를 완료·제거한다(null 반환).
     * 진행 중이면 orderCreateNotComplete를 보내고 emitter를 유지한다.
     * 클라이언트가 작업 종료 뒤에 늦게 연결해도 결과를 받도록 두 종료 상태를 모두 처리한다.
     */
    public SseEmitter checkOrderStateThenSend(final String uuid, final OrderProcessingContext context) {
        return map.computeIfPresent(uuid, (key, emitter) -> {
            OrderJobStatus status = context.getOrderJobState().status();
            try {
                if (status == OrderJobStatus.COMPLETED) {
                    emitter.send(SseEmitter.event().name("orderCreateComplete").data(context.getOrderJobState()));
                    emitter.complete();
                    return null;
                }
                if (status == OrderJobStatus.FAILED) {
                    emitter.send(SseEmitter.event().name("orderCreateFail").data(context.getOrderIdentifier()));
                    emitter.complete();
                    return null;
                }
                emitter.send(SseEmitter.event().name("orderCreateNotComplete").data(context.getOrderIdentifier()));
                return emitter;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void sendFail(String orderUuid, OrderProcessingContext context) {
        map.computeIfPresent(orderUuid, (key, emitter) -> {
            if (context.getOrderJobState().status() == OrderJobStatus.FAILED) {
                try {
                    emitter.send(SseEmitter.event().name("orderCreateFail").data(context.getOrderIdentifier()));
                    emitter.complete();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        });
    }
}
