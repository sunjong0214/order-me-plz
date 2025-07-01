package com.omp.delivery.dto;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryRequest {
    @Positive
    private final Long orderId;
}
