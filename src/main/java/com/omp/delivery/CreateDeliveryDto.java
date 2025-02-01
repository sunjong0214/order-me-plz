package com.omp.delivery;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryDto {
    private final DeliveryStatus status;

    public static Delivery of(CreateDeliveryDto dto) {
        return new Delivery(dto.status);
    }
}
