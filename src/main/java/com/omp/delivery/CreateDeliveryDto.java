package com.omp.delivery;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryDto {
    @NotNull
    private final DeliveryStatus status;

    public static Delivery of(final CreateDeliveryDto dto) {
        return new Delivery(dto.status);
    }
}
