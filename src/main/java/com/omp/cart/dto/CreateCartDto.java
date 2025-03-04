package com.omp.cart.dto;

import com.omp.cart.Cart;
import com.omp.cart.CartMenus;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateCartDto {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    private final CartMenus cartMenus;

    public static Cart from(final CreateCartDto createCartDto) {
        return new Cart(createCartDto.userId, createCartDto.shopId, createCartDto.cartMenus);
    }
}
