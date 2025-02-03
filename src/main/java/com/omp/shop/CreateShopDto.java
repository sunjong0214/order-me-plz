package com.omp.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateShopDto {
    @NotBlank
    private final String name;
    @NotNull
    private final ShopCategory category;

    public static Shop of(final CreateShopDto dto) {
        return new Shop(dto.name, dto.category);
    }
}
