package com.omp.menu.dto;

import com.omp.menu.Menu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateMenuDto(
        @Positive Long shopId,
        @PositiveOrZero int quantity,
        @NotBlank String name,
        @PositiveOrZero int price,
        @NotBlank String description) {

    public static Menu from(final CreateMenuDto createMenuDto) {
        return new Menu(createMenuDto.shopId, createMenuDto.quantity, createMenuDto.name, createMenuDto.price,
                createMenuDto.description);
    }
}
