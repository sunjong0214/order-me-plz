package com.omp.shop;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ShopResponse {
    private final String name;
    private final boolean isOpen;
    private final double reviewRate;
}
