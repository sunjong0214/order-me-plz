package com.omp.delivery.dto;

import com.omp.delivery.Delivery;
import com.omp.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryDto {
    @Positive
    private final Long orderId;

    public static Delivery from(final CreateDeliveryDto dto) {
        return new Delivery(dto.orderId);
    }
}
