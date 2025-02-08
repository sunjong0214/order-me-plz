package com.omp.delivery.dto;

import com.omp.delivery.Delivery;
import com.omp.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryDto {
    @NotNull
    private final DeliveryStatus status;

    public static Delivery from(final CreateDeliveryDto dto) {
        return new Delivery(dto.status);
    }
}
