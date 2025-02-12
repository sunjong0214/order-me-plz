package com.omp.order.dto;

import com.omp.order.Order;
import com.omp.order.OrderMenus;
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
    private final OrderMenus orderMenus;

    public static Order from(final CreateOrderDto createOrderDto) {
        return new Order(createOrderDto.deliveryId, createOrderDto.ordererId, createOrderDto.shopId, createOrderDto.orderMenus);
    }
}
