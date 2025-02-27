package com.omp.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMenuDto(
        @NotBlank String name,
        @PositiveOrZero Integer price,
        @PositiveOrZero Integer quantity,
        Boolean isSoldOut,
        String description) {
}
