package com.omp.order.dto;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateOrderRequest {
    @Positive
    private final Long deliveryId;
    @Positive
    private final Long ordererId;
    @Positive
    private final Long shopId;
    @Positive
    private final Long cartId;

    public static CreateOrderDto from(final CreateOrderRequest request) {
        return new CreateOrderDto(request.deliveryId, request.ordererId, request.shopId, request.cartId);
    }
}
