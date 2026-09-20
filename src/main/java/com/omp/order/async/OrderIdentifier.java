package com.omp.order.async;

public record OrderIdentifier(Long ordererId, Long shopId, Long cartId) {
}
