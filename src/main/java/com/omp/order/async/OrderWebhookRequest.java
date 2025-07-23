package com.omp.order.async;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class OrderWebhookRequest {
    @NotBlank
    private final String jobUuid;
    @NotNull
    private final OrderJobStatus status;
    @Positive
    private final Long orderId;
}
