package com.omp.shop.dto;

import com.omp.shop.ShopCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateShopRequest {
    @NotBlank
    private final String name;
    @NotNull
    private final ShopCategory category;

    public static CreateShopDto from(final CreateShopRequest request) {
        return new CreateShopDto(request.getName(), request.getCategory());
    }
}
