package com.omp.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateMenuRequest {
    @Positive
    private final Long shopId;
    @NotBlank
    private final String name;
    @PositiveOrZero
    private final int price;
    @NotBlank
    private final String description;

    public static CreateMenuDto from(CreateMenuRequest request) {
        return new CreateMenuDto(request.shopId, request.name, request.price, request.description);
    }
}
