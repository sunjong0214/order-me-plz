package com.omp.shop;

import com.omp.shop.dto.ShopInfo;
import org.springframework.data.domain.Slice;

public interface ShopRepositoryCustom {
    Slice<ShopInfo> findBy(ShopCategory category, Long cursor, int pageSize);
}
