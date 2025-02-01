package com.omp.shop;

import com.omp.cart.Cart;
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
        return shopRepository.save(CreateShopDto.of(createShopDto)).getId();
    }
}
