package com.omp.menu;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateMenuDto {
    private final String name;
    private final Integer price;
    private final String description;

    public static Menu of(final CreateMenuDto createMenuDto) {
        return new Menu(createMenuDto.name, createMenuDto.price, createMenuDto.description);
    }
}
