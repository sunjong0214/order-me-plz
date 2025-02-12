package com.omp.cart.dto;

import com.omp.menu.Menus;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateCartRequest {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    private final Menus menus;

    public static CreateCartDto from(final CreateCartRequest request) {
        return new CreateCartDto(request.userId, request.shopId, request.menus);
    }
}
