package com.omp.shop;

import com.omp.shop.dto.CreateShopRequest;
import com.omp.shop.dto.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/shops")
public class ShopController {
    private final ShopService shopService;

    @GetMapping
    @Cacheable(cacheNames = "shops")
    public Slice<ShopInfo> getShopById(final @RequestParam ShopCategory category, final @RequestParam Long cursor, final @RequestParam int pageSize) {
        return shopService.findShopBy(category, cursor, pageSize);
    }

    @PostMapping
    public Long createShop(final @RequestBody CreateShopRequest request) {
        return shopService.saveShopBy(CreateShopRequest.from(request));
    }

//    @GetMapping("/list/{cursor}")
//    public Slice<ShopResponse> getShopsBy(final @PathVariable Long cursor) {
//        shopService.findShopsBy(cursor);
//    }
}
