package com.omp.cart;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CartResponse {
    private final Long cartId;
    private final Long orderMenuId;
}
