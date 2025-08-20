package com.omp.delivery.dto;

import com.omp.order.Order;
import com.omp.orderMenu.OrderMenu;
import com.omp.orderMenu.OrderMenuRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Data
public class CreateAsyncOrderEvent {
    @Positive
    private final Long ordererId;
    @Positive
    private final Long cartId;
    @Positive
    private final Long shopId;
    @NotBlank
    private final String uuid;
    private List<OrderMenuRequest> orderMenus;

    public static Order from (CreateAsyncOrderEvent event) {
        return new Order(event.ordererId, event.cartId, event.shopId);
    }
    public static List<OrderMenu> from(final List<OrderMenuRequest> orderMenus) {
        return orderMenus.stream()
                .map(OrderMenuRequest::from)
                .toList();
    }
}
