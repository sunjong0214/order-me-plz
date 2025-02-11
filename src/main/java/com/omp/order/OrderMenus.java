package com.omp.order;

import com.omp.menu.Menu;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Embeddable
public class OrderMenus {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Menu> orderMenus = new ArrayList<>();
}
