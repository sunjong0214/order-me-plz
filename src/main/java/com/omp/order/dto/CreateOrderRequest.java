package com.omp.order.dto;

import com.omp.menu.dto.MenuRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateOrderRequest {
    @Positive
    private final Long deliveryId;
    @Positive
    private final Long ordererId;
    @Positive
    private final Long shopId;
    @NotNull
    private final List<MenuRequest> orderMenus;

    public static CreateOrderDto from(final CreateOrderRequest request) {
        return new CreateOrderDto(request.deliveryId, request.ordererId, request.shopId, request.orderMenus);
    }
}
