package com.omp.review;

import com.omp.review.dto.CreateReviewDto;
import com.omp.shop.Shop;
import com.omp.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [bench/review-before] 개선 전 설계 재구성. 리뷰 INSERT 와 shops 행의 통계 UPDATE 를 같은 트랜잭션에서 수행한다.
 *  1. reviewRepository.save → INSERT reviews (IDENTITY 라 즉시 실행). FK 검사가 shops 행에 S락.
 *  2. shop.applyReviewRating → 커밋 직전 flush 에서 UPDATE shops → 같은 행에 X락 요구.
 * 같은 가게에 동시 요청 두 건이 1 을 각각 마친 상태면 서로의 S락을 기다리며 데드락 → InnoDB 가 한쪽을 롤백(500).
 * 데드락이 아닌 겹침에서는 읽고-계산-쓰기가 이전 값을 덮어써 통계 유실이 생긴다 (benchmark/sql/verify.sql 4-a).
 * main 의 omp.review.stats.mode 와 shop_review_stats 는 이 브랜치에서 사용하지 않는다 (ReviewStats*Test 는 실패한다).
 */
@RequiredArgsConstructor
@Service
@Transactional
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;

    public Review findReviewBy(final Long id) {
        return reviewRepository.findById(id).orElseThrow();
    }

    public Long saveReviewBy(final CreateReviewDto dto) {
        Shop shop = shopRepository.findById(dto.getShopId()).orElseThrow();
        Review review = reviewRepository.save(CreateReviewDto.from(dto, shop));

        shop.applyReviewRating(review.getRating());

        return review.getId();
    }
}
