package com.omp.review;

import com.omp.review.dto.CreateReviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/{id}")
    public Review getReviewBy(final @PathVariable Long id) {
        return reviewService.findReviewBy(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Long> createReview(final @RequestBody CreateReviewRequest request) {
        return ResponseEntity.ok(reviewService.saveReviewBy(CreateReviewRequest.from(request)));
    }
}
