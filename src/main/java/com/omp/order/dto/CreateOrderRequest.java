package com.omp.order.dto;

import com.omp.order.Order;
import com.omp.orderMenu.OrderMenu;
import com.omp.orderMenu.OrderMenuRequest;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Data
public class CreateOrderRequest {
    @Positive
    private final Long ordererId;
    @Positive
    private final Long cartId;
    @Positive
    private final Long shopId;
    private List<OrderMenuRequest> orderMenus;

    public static Order from(final CreateOrderRequest request) {
        return new Order(request.ordererId, request.cartId, request.shopId);
    }

    public static List<OrderMenu> from(final List<OrderMenuRequest> orderMenus) {
        return orderMenus.stream()
                .map(OrderMenuRequest::from)
                .toList();
    }
}
