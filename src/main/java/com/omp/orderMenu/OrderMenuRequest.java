package com.omp.orderMenu;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class OrderMenuRequest {
    private final Long menuId;
    private final Long cartId;
    private final int quantity;

    public static OrderMenu from(OrderMenuRequest request) {
        return new OrderMenu(request.menuId, request.cartId, request.quantity);
    }
}
