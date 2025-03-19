package com.omp.shop;

import com.omp.shop.dto.CreateShopDto;
import com.omp.shop.dto.CreateShopRequest;
import com.omp.shop.dto.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ShopService {
    private final ShopRepository shopRepository;
    private final ShopRepositoryCustom shopRepositoryCustomImpl;

    public Slice<ShopInfo> findShopBy(final ShopCategory category, final Long cursor, final int pageSize) {
        return shopRepositoryCustomImpl.findBy(category, cursor, pageSize);
    }

    public Long saveShopBy(final CreateShopDto dto) {
        return shopRepository.save(CreateShopDto.from(dto)).getId();
    }
}
