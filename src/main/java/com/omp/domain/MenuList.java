package com.omp.domain;

import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Embeddable
public class MenuList {
    private final List<Menu> menuList = new ArrayList<Menu>();
}
