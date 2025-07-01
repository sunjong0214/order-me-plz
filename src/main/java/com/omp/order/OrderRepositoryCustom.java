package com.omp.order;

public interface OrderRepositoryCustom {
    OrderValidateDto validateOrder(Long ordererId, Long shopId, Long cartId);
}
