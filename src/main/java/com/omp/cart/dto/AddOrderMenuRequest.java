package com.omp.cart.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class AddOrderMenuRequest {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    @Positive
    private final Long cartId;
    @Positive
    private final Long menuId;
    @Positive
    private final int quantity;
//    private final OrderMenuRequest orderMenuRequest;
}
