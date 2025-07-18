package com.omp.order;

import com.omp.order.dto.OrderValidateDto;

public interface OrderRepositoryCustom {
    OrderValidateDto validateOrder(Long ordererId, Long shopId, Long cartId);
}
