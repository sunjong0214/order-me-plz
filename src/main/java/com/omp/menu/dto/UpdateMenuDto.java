package com.omp.menu.dto;

import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMenuDto(
        @NotBlank
        String name,
        @PositiveOrZero
        int price,
        @Version
        @PositiveOrZero
        int quantity,
        String description) {
}
