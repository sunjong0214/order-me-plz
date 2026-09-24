package com.omp.review;

import com.omp.review.dto.CreateReviewDto;
import com.omp.review.dto.CreateReviewEvent;
import com.omp.shop.ReviewStatsProperties;
import com.omp.shop.Shop;
import com.omp.shop.ShopRepository;
import com.omp.shop.ShopReviewStatsWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;
    private final ShopReviewStatsWriter statsWriter;
    private final ReviewStatsProperties statsProperties;
    private final ApplicationEventPublisher eventPublisher;

    public Review findReviewBy(final Long id) {
        return reviewRepository.findById(id).orElseThrow();
    }

    public Long saveReviewBy(final CreateReviewDto dto) {
        Shop shop = shopRepository.findById(dto.getShopId()).orElseThrow();
        Review review = reviewRepository.save(CreateReviewDto.from(dto, shop));

        if (statsProperties.isSync()) {
            // 채택: 같은 트랜잭션에서 원자적으로 반영. 통계 행이 shops와 분리되어 기존 S락→X락 승격 경로가 없다.
            statsWriter.apply(review.getId(), shop.getId(), review.getRating());
        } else {
            // 비교군: 커밋 후 별도 스레드에서 반영. 응답에서 stats 행 락 대기를 빼는 대신 반영 지연·복구 없는 누락 경로가 생긴다.
            eventPublisher.publishEvent(
                    new CreateReviewEvent(review.getId(), shop.getId(), review.getRating()));
        }

        return review.getId();
    }
}
