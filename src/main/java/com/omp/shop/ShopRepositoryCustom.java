package com.omp.shop;

import com.omp.shop.dto.ShopInfo;
import com.omp.shop.dto.ShopUpdateRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Slice;

public interface ShopRepositoryCustom {
    Slice<ShopInfo> findBy(ShopCategory category, Long cursor, int pageSize);

    ShopInfo findDetailBy(Long id);

    void updateByCond(ShopUpdateRequest request);

    boolean isOpenById(@Positive Long shopId);
}
