package com.omp.order.dto;

import com.omp.order.Order;
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
    @Positive
    private final Long cartId;


}
