package com.omp.order;

import static com.omp.order.async.OrderJobStatus.FAILED;
import static com.omp.order.async.OrderJobStatus.PROCESSING;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;
import static org.springframework.http.HttpStatus.SEE_OTHER;
import static org.springframework.http.ResponseEntity.accepted;

import com.omp.order.async.OrderJobState;
import com.omp.order.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/order")
public class OrderController {
    private final OrderService orderService;
    private final SseEmitterService sseEmitterService;

    @GetMapping("/{id}")
    public Order getOrder(final @PathVariable Long id) {
        return orderService.findOrderBy(id);
    }

    @PostMapping
    public Long createOrder(final @RequestBody CreateOrderRequest request) {
        return orderService.saveOrderBy(request);
    }

    @PostMapping("/async")
    @ResponseStatus(ACCEPTED)
    public ResponseEntity<String> asyncCreateOrder(final @RequestBody CreateOrderRequest request) {
        String uuid = orderService.asyncOrder(request);
        return accepted()
                .header("Location", "/api/v1/order/" + uuid)
                .build();
    }

    @GetMapping(value = "/sse/{orderUuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter orderStream(final @PathVariable String orderUuid) {
        return sseEmitterService.createOrderSse(orderUuid);
    }

    @GetMapping("/async/{uuid}")
    public ResponseEntity<Object> checkOrderStatus(final @PathVariable String uuid) {
        OrderJobState orderState = orderService.getOrderState(uuid);

        if (orderState.status() == FAILED) {
            return ResponseEntity
                    .status(NOT_ACCEPTABLE)
                    .body(orderService.getOrderIdentifier(uuid));
        } else if (orderState.status() == PROCESSING) {
            return ResponseEntity
                    .status(ACCEPTED)
                    .header("Location", "/api/v1/order/async" + uuid)
                    .body(orderState);
        }

        // COMPLETED
        return ResponseEntity
                .status(SEE_OTHER)
                .header("Location", "/api/v1/order" + orderState.orderId())
                .body(orderState);
    }
}
