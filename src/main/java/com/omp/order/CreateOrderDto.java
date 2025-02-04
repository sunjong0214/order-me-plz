package com.omp.order;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateOrderDto {
    @Positive
    private final Long deliveryId;
    @Positive
    private final Long ordererId;
    @Positive
    private final Long shopId;

    public static Order from(final CreateOrderDto createOrderDto) {
        return new Order(createOrderDto.deliveryId, createOrderDto.ordererId, createOrderDto.shopId);
    }
}
