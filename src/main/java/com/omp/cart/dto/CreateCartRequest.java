package com.omp.cart.dto;

import com.omp.cart.CartMenus;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateCartRequest {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    private final CartMenus cartMenus;

    public static CreateCartDto from(final CreateCartRequest request) {
        return new CreateCartDto(request.userId, request.shopId, request.cartMenus);
    }
}
