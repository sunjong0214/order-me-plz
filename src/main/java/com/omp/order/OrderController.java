package com.omp.order;

import com.omp.order.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/order")
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{id}")
    public Order getOrder(final @PathVariable Long id) {
        return orderService.findOrderBy(id);
    }

    @PostMapping
    public Long createOrder(final @RequestBody CreateOrderRequest request) {
        return orderService.saveOrderBy(CreateOrderRequest.from(request));
    }
}
