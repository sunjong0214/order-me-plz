//package com.omp.shop;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//import com.omp.review.Review;
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.Callable;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.stream.IntStream;
//import org.junit.jupiter.api.Test;
//
//class ShopReviewsTest {
//
//    @Test
//    void 리뷰_작성_시_기존_리뷰평점_업데이트_동시성_보장_테스트() throws InterruptedException {
//        Shop shop = new Shop("fff", ShopCategory.PIZZA);
//        Review review = new Review("test", 1L, "test", BigDecimal.valueOf(3), shop);
//        shop.updateRatingAverage(BigDecimal.valueOf(5));
//        List<Review> reviews = new ArrayList<>();
//        reviews.add(review);
//
//        int threadCount = 100;
//        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
//
//        // 각 스레드가 수행할 작업
//        Callable<Double> reviewAddTask = () -> {
//            Review newReview = new Review("test", 1L, "test", BigDecimal.valueOf(3), shop); // 모든 리뷰는 5.0 점수
//            reviews.add(newReview);
//            // 동시에 리뷰 추가 및 평균 계산
//            return shop.updateRatingAverage(newReview.getRating());
//        };
//
//        // 모든 스레드에서 작업 실행
//        List<Future<Double>> futures = IntStream.range(0, threadCount)
//                .mapToObj(i -> executorService.submit(reviewAddTask))
//                .toList();
//
//        // 모든 작업 완료 대기
//        executorService.shutdown();
//        executorService.awaitTermination(1, TimeUnit.MINUTES);
//        assertEquals(threadCount + 1, reviews.size());
//        System.out.println(shop.getRawRatingAverage());
//        assertEquals(3.2, shop.getRatingAverage());
//    }
//}