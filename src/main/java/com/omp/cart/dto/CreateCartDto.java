package com.omp.cart.dto;

import com.omp.cart.Cart;
import com.omp.menu.MenuList;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateCartDto {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    private final MenuList menuList;

    public static Cart from(final CreateCartDto createCartDto) {
        return new Cart(createCartDto.userId, createCartDto.shopId, createCartDto.menuList);
    }
}
