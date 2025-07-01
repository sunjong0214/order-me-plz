package com.omp.order;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class OrderValidateDto {
    private final boolean validOrderer;
    private final boolean validShop;
    private final boolean validCart;

    public boolean isValid() {
        return validOrderer || validShop || validCart;
    }
}
