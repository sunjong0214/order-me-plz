package com.omp.menu.dto;

import com.omp.menu.Menu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateMenuDto {
    @Positive
    private final Long shopId;
    @NotBlank
    private final String name;
    @PositiveOrZero
    private final int price;
    @NotBlank
    private final String description;

    public static Menu from(final CreateMenuDto createMenuDto) {
        return new Menu(createMenuDto.shopId, createMenuDto.name, createMenuDto.price, createMenuDto.description);
    }
}
