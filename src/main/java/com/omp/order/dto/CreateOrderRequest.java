package com.omp.order.dto;

import com.omp.order.OrderMenus;
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
    private final OrderMenus orderMenus;

    public static CreateOrderDto from(final CreateOrderRequest request) {
        return new CreateOrderDto(request.deliveryId, request.ordererId, request.shopId, request.orderMenus);
    }
}
