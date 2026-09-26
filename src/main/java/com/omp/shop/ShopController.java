package com.omp.shop;

import com.omp.shop.dto.CreateShopRequest;
import com.omp.shop.dto.ShopInfo;
import com.omp.shop.dto.ShopUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * 카테고리별 가게 목록(커서 페이지, shop_id 내림차순). 첫 페이지는 cursor 없이, 다음 페이지는 이전 페이지의 마지막 shopId를 cursor로.
     * 캐시 키 = (category, cursor, pageSize). sync = true: 만료된 같은 키에 동시에 들어온 요청은 한 요청만 DB를 조회하고 나머지는 그 결과를 기다린다.
     */
    @GetMapping
    @Cacheable(cacheNames = "shops", sync = true)
    public Slice<ShopInfo> getShops(final @RequestParam ShopCategory category,
                                    final @RequestParam(required = false) Long cursor,
                                    final @RequestParam int pageSize) {
        return shopService.findShopsBy(category, cursor, pageSize);
    }

    @GetMapping("/{id}")
    public ShopInfo getShopById(final @PathVariable Long id) {
        return shopService.findShopBy(id);
    }

    @PostMapping
    public Long createShop(final @RequestBody CreateShopRequest request) {
        return shopService.saveShopBy(CreateShopRequest.from(request));
    }

    @PatchMapping
    public ResponseEntity<Void> patchStatus(final @RequestBody ShopUpdateRequest request) {
        shopService.updateStatus(request);
        return ResponseEntity.ok().build();
    }

//    @GetMapping("/list/{cursor}")
//    public Slice<ShopResponse> getShopsBy(final @PathVariable Long cursor) {
//        shopService.findShopsBy(cursor);
//    }
}
