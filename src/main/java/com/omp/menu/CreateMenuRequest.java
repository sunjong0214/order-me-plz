package com.omp.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateMenuRequest {
    @NotBlank
    private final String name;
    @PositiveOrZero
    private final int price;
    @NotBlank
    private final String description;

    public static CreateMenuDto from(CreateMenuRequest request) {
        return new CreateMenuDto(request.name, request.price, request.description);
    }
}
