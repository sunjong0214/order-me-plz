package com.omp.menu.dto;

import com.omp.menu.Menu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MenuRequest {
    @Positive
    private final Long shopId;
    @NotBlank
    private final String name;
    @PositiveOrZero
    private final int price;
    private final String description;

    public static Menu from(MenuRequest request) {
        return new Menu(request.shopId, request.name, request.price, request.description);
    }
}
