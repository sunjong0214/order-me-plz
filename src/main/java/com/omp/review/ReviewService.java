package com.omp.review;

import com.omp.review.dto.CreateReviewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;

    public Review findReviewBy(final Long id) {
        return reviewRepository.findById(id).orElseThrow();
    }

    public Long saveReviewBy(final CreateReviewDto createReviewDto) {
        return reviewRepository.save(CreateReviewDto.from(createReviewDto)).getId();
    }
}
