package com.omp.menu.dto;

import com.omp.menu.Menu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record MenuRequest(
        @Positive Long shopId,
        @NotBlank String name,
        @PositiveOrZero int price,
        @PositiveOrZero int quantity,
        String description) {

    public static Menu from(MenuRequest request) {
        return new Menu(request.shopId, request.quantity, request.name, request.price, request.description);
    }
}
