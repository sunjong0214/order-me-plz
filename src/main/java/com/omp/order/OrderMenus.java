package com.omp.order;

import com.omp.menu.Menu;
import com.omp.menu.dto.MenuRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Embeddable
public class OrderMenus {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Menu> orderMenus = new ArrayList<>();

    public OrderMenus(List<Menu> orderMenus) {
        this.orderMenus = orderMenus;
    }

    public OrderMenus() {
    }

    public static OrderMenus of(List<MenuRequest> orderMenus) {
        return new OrderMenus(orderMenus.stream().map(MenuRequest::from).toList());
    }
}
