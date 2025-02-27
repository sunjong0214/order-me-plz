package com.omp.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeMenuRequest(
        @NotBlank String name,
        @PositiveOrZero Integer price,
        @PositiveOrZero Integer quantity,
        Boolean isSoldOut,
        String description) {

    public static UpdateMenuDto from(final ChangeMenuRequest request) {
        return new UpdateMenuDto(request.name(), request.price(), request.quantity(), request.isSoldOut(), request.description());
    }
}
