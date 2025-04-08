package com.omp.shop;

import com.omp.review.ReviewRepository;
import com.omp.shop.dto.CreateShopDto;
import com.omp.shop.dto.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class ShopService {
    private final ShopRepository shopRepository;
    private final ReviewRepository reviewRepository;

    public Slice<ShopInfo> findShopsBy(final ShopCategory category, final Long cursor, final int pageSize) {
        return shopRepository.findBy(category, cursor, pageSize);
    }

    public Long saveShopBy(final CreateShopDto dto) {
        return shopRepository.save(CreateShopDto.from(dto)).getId();
    }

    public ShopInfo findShopBy(final Long id) {
        ShopInfo shopInfo = shopRepository.findDetailBy(id);
        shopInfo.setRecentlyReviewDetail(reviewRepository.getReviewDetails(id));
        return shopInfo;
    }

    public void updateStatus(ShopUpdateRequest request) {
        shopRepository.updateByCond(request);
    }
}
