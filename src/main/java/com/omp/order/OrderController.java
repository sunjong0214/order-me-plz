package com.omp.order;

import static com.omp.order.async.OrderJobStatus.FAILED;
import static com.omp.order.async.OrderJobStatus.PROCESSING;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.SEE_OTHER;
import static org.springframework.http.ResponseEntity.accepted;

import com.omp.order.async.OrderJobState;
import com.omp.order.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
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
                .header("Location", "/api/v1/order/sse/" + uuid)
                .build();
    }

    @GetMapping(value = "/sse/{orderUuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter orderStream(final @PathVariable String orderUuid) {
        return sseEmitterService.createOrderSse(orderUuid);
    }

    /**
     * 폴링 계약: 처리 중 202(+ 자기 자신 Location), 완료 303(+ 주문 리소스 Location), 실패 200 + FAILED 상태, 모르는 uuid 404.
     * 실패는 조회 자체는 성공했고 작업 결과가 실패라는 뜻이므로 200 본문의 상태로 표현한다.
     * (이전의 406 Not Acceptable 은 표현 형식 협상 실패를 뜻해 의미가 맞지 않았다.)
     */
    @GetMapping("/async/{uuid}")
    public ResponseEntity<OrderJobState> checkOrderStatus(final @PathVariable String uuid) {
        OrderJobState orderState = orderService.getOrderState(uuid);

        if (orderState.status() == FAILED) {
            return ResponseEntity.ok(orderState);
        } else if (orderState.status() == PROCESSING) {
            return ResponseEntity
                    .status(ACCEPTED)
                    .header("Location", "/api/v1/order/async/" + uuid)
                    .body(orderState);
        }

        // COMPLETED
        return ResponseEntity
                .status(SEE_OTHER)
                .header("Location", "/api/v1/order/" + orderState.orderId())
                .body(orderState);
    }
}
