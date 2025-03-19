package com.omp.shop.dto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ShopInfo {
    private final Long shopId;
    private final String name;
    private final boolean isOpen;
}
