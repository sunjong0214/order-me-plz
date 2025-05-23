package com.omp.order.dto;

import com.omp.order.Order;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class CreateOrderRequest {
    @Positive
    private final Long ordererId;
    @Positive
    private final Long cartId;
    @Positive
    private final Long shopId;

    public static Order from(final CreateOrderRequest request) {
        return new Order(request.ordererId, request.cartId, request.shopId);
    }
}
