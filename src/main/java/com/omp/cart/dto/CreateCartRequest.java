package com.omp.cart.dto;

import com.omp.cart.CartMenus;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class CreateCartRequest {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    private final List<Long> menuIds;
}
