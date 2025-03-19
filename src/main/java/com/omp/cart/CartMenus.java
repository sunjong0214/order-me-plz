package com.omp.cart;

import com.omp.menu.Menu;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
public class CartMenus {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Menu> cartMenus = new ArrayList<>();

}
