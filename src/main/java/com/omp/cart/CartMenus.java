package com.omp.cart;

import com.omp.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class CartMenus {
    private List<Menu> cartMenus = new ArrayList<>();

    public void addAll(List<Menu> menus) {
        cartMenus.addAll(menus);
    }
}
