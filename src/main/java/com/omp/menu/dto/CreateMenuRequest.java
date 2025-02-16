package com.omp.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

@Getter
public record CreateMenuRequest(
        @Positive Long shopId,
        @NotBlank String name,
        @PositiveOrZero int price,
        @PositiveOrZero int quantity,
        @NotBlank String description) {

    public static CreateMenuDto from(CreateMenuRequest request) {
        return new CreateMenuDto(request.shopId, request.quantity, request.name, request.price, request.description);
    }
}
