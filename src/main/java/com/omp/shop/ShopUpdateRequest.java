package com.omp.shop;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class ShopUpdateRequest {
    private final Long id;
    private final Boolean isOpen;
    private final ShopCategory category;
    private final String name;
}
