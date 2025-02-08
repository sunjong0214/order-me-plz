package com.omp.shop;

import com.omp.shop.dto.CreateShopDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ShopService {
    private final ShopRepository shopRepository;

    public Shop findShopBy(final Long id) {
        return shopRepository.findById(id).orElseThrow();
    }

    public Long saveShopBy(final CreateShopDto createShopDto) {
        return shopRepository.save(CreateShopDto.from(createShopDto)).getId();
    }
}
