package com.omp.delivery;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryDto {
    private final DeliveryStatus status;

    public static Delivery of(final CreateDeliveryDto dto) {
        return new Delivery(dto.status);
    }
}
