package com.omp.shop;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {
    private final ShopService shopService;

    @GetMapping("/{id}")
    public Shop getShopById(final @PathVariable Long id) {
        return shopService.findShopBy(id);
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
