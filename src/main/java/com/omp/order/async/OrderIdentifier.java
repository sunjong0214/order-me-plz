package com.omp.order.async;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderIdentifier {
    private final Long ordererId;
    private final Long shopId;
    private final Long cartId;
}
