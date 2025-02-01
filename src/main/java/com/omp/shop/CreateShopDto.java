package com.omp.shop;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateShopDto {
    private final String name;
    private final ShopCategory category;

    public static Shop of(final CreateShopDto dto) {
        return new Shop(dto.name, dto.category);
    }
}
