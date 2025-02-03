package com.omp.cart;

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

    public static Cart to(final CreateCartDto createCartDto) {
        return new Cart(createCartDto.userId, createCartDto.shopId, createCartDto.menuList);
    }
}
