package com.omp.delivery.dto;

import com.omp.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryRequest {
    @Positive
    private final Long orderId;

    public static CreateDeliveryDto from(final CreateDeliveryRequest request) {
        return new CreateDeliveryDto(request.orderId);
    }
}
