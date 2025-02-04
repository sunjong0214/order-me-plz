package com.omp.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateShopRequest {
    @NotBlank
    private final String name;
    @NotNull
    private final ShopCategory category;

    public static CreateShopDto of(final CreateShopRequest request) {
        return new CreateShopDto(request.name, request.category);
    }
}
