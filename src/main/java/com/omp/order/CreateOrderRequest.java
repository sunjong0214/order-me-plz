package com.omp.order;

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

    public static CreateOrderDto of(final CreateOrderRequest request) {
        return new CreateOrderDto(request.deliveryId, request.ordererId, request.shopId);
    }
}
