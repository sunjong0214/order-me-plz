package com.omp.review;

import com.omp.review.dto.CreateReviewDto;
import com.omp.shop.Shop;
import com.omp.shop.ShopRepository;
import com.omp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Service
@Transactional
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Review findReviewBy(final Long id) {
        return reviewRepository.findById(id).orElseThrow();
    }

    public Long saveReviewBy(final CreateReviewDto dto) {
        Shop shop = shopRepository.findById(dto.getShopId()).orElseThrow();
        Review review = reviewRepository.save(CreateReviewDto.from(dto, shop));

        eventPublisher.publishEvent(
                new CreateReviewEvent(review.getId(), shop.getId(), review.getRating()));

        return review.getId();
    }
}
