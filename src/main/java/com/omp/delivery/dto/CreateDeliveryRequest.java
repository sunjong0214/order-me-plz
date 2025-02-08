package com.omp.delivery.dto;

import com.omp.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateDeliveryRequest {
    @NotNull
    private final DeliveryStatus status;

    public static CreateDeliveryDto from(final CreateDeliveryRequest request) {
        return new CreateDeliveryDto(request.status);
    }
}
