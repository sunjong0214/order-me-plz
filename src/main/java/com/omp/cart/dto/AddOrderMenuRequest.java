package com.omp.cart.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;

@RequiredArgsConstructor
@Data
public class AddOrderMenuRequest {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    @Positive
    @Nullable
    private final Long cartId;
    @Positive
    private final Long menuId;
    @Positive
    private final int quantity;
    @PositiveOrZero
    private final int orderedPrice;
//    private final OrderMenuRequest orderMenuRequest;
}
