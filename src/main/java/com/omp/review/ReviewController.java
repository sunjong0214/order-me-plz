package com.omp.review;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/review")
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/{id}")
    public Review getReviewBy(final @PathVariable Long id) {
        return reviewService.findReviewBy(id);
    }

    @PostMapping
    public Long createReview(final @RequestBody CreateReviewDto createReviewDto) {
        return reviewService.saveReviewBy(createReviewDto);
    }
}
