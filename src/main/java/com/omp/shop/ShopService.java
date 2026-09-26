package com.omp.shop;

import com.omp.review.ReviewRepository;
import com.omp.shop.dto.CreateShopDto;
import com.omp.shop.dto.ShopInfo;
import com.omp.shop.dto.ShopUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class ShopService {
    private final ShopRepository shopRepository;
    private final ShopReviewStatsRepository shopReviewStatsRepository;
    private final ReviewRepository reviewRepository;

    public Slice<ShopInfo> findShopsBy(final ShopCategory category, final Long cursor, final int pageSize) {
        return shopRepository.findBy(category, cursor, pageSize);
    }

    // 새 가게는 목록의 페이지 경계를 밀기 때문에 목록 캐시 전체를 비운다 (트랜잭션 인식 캐시라 커밋 후 실행).
    @CacheEvict(cacheNames = "shops", allEntries = true)
    public Long saveShopBy(final CreateShopDto dto) {
        Shop shop = shopRepository.save(CreateShopDto.from(dto));
        shopReviewStatsRepository.save(new ShopReviewStats(shop.getId()));
        return shop.getId();
    }

    public ShopInfo findShopBy(final Long id) {
        ShopInfo shopInfo = shopRepository.findDetailBy(id);
        shopInfo.setRecentlyReviewDetail(reviewRepository.getReviewDetails(id));
        return shopInfo;
    }

    // 영업 상태·이름·카테고리는 즉시 반영돼야 하므로 목록 캐시를 비운다. 가게 변경은 드물어 전체를 비워도 부담이 작다.
    @CacheEvict(cacheNames = "shops", allEntries = true)
    public void updateStatus(ShopUpdateRequest request) {
        shopRepository.updateByCond(request);
    }
}
