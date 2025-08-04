package com.omp.order.async;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderFailedWebhookRequest {
    @Positive
    private final Long ordererId;
    @Positive
    private final Long cartId;
    @Positive
    private final Long shopId;
    @NotBlank
    private final String uuid;
    @NotBlank
    private final String exceptionMessage;
}
