package com.omp.menu;

import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;

@Embeddable
public class MenuList {
    private final List<Menu> menuList = new ArrayList<>();
}
