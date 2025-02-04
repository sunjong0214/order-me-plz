package com.omp.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateMenuDto {
    @NotBlank
    private final String name;
    @PositiveOrZero
    private final int price;
    @NotBlank
    private final String description;

    public static Menu from(final CreateMenuDto createMenuDto) {
        return new Menu(createMenuDto.name, createMenuDto.price, createMenuDto.description);
    }
}
