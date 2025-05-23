package com.omp.cart;

import com.omp.orderMenu.OrderMenu;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
public class OrderMenus {
    private final List<OrderMenu> orderMenus = new ArrayList<>();

    public void addAll(List<OrderMenu> newOrderMenus) {
        this.orderMenus.addAll(newOrderMenus);
    }

    public void add(OrderMenu orderMenus) {
        this.orderMenus.add(orderMenus);
    }
}
