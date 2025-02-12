package com.omp.order.dto;

import com.omp.menu.dto.MenuRequest;
import com.omp.order.Order;
import com.omp.order.OrderMenus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateOrderDto {
    @Positive
    private final Long deliveryId;
    @Positive
    private final Long ordererId;
    @Positive
    private final Long shopId;
    @NotNull
    private final List<MenuRequest> orderMenus;

    public static Order from(final CreateOrderDto createOrderDto) {
        return new Order(createOrderDto.deliveryId, createOrderDto.ordererId, createOrderDto.shopId,
                OrderMenus.of(createOrderDto.orderMenus));
    }
}
