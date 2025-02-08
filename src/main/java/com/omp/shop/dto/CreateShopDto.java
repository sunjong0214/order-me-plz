package com.omp.shop.dto;

import com.omp.shop.Shop;
import com.omp.shop.ShopCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateShopDto {
    @NotBlank
    private final String name;
    @NotNull
    private final ShopCategory category;

    public static Shop from(final CreateShopDto dto) {
        return new Shop(dto.name, dto.category);
    }
}
