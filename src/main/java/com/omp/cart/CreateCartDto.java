package com.omp.cart;

import com.omp.menu.MenuList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateCartDto {
    private final Long userId;
    private final Long shopId;
    private final MenuList menuList;

    public static Cart to(final CreateCartDto createCartDto) {
        return new Cart(createCartDto.userId, createCartDto.shopId, createCartDto.menuList);
    }
}
