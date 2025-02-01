package com.omp.order;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateOrderDto {
    private final Long deliveryId;
    private final Long paymentId;
    private final Long shopId;

    public static Order of(final CreateOrderDto createOrderDto) {
        return new Order(createOrderDto.deliveryId, createOrderDto.paymentId, createOrderDto.shopId);
    }
}
