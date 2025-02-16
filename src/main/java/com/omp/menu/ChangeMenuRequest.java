package com.omp.menu;

import com.omp.menu.dto.UpdateMenuDto;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeMenuRequest(
        @NotBlank
        String name,
        @PositiveOrZero
        int price,
        @Version
        @PositiveOrZero
        int quantity,
        String description) {

    public static UpdateMenuDto from(final ChangeMenuRequest request) {
        return new UpdateMenuDto(request.name(), request.price(), request.quantity(), request.description());
    }
}
