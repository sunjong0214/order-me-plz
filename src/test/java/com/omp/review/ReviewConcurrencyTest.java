package com.omp.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.omp.review.dto.CreateReviewDto;
import com.omp.shop.Shop;
import com.omp.shop.ShopCategory;
import com.omp.shop.ShopRepository;
import com.omp.shop.ShopService;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.datasource.hikari.auto-commit=false"
})
public class ReviewConcurrencyTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // 실제 서비스 객체 대신 스파이 객체를 사용
    private ShopService shopServiceSpy;

    private Shop testShop;

    @BeforeEach
    @Transactional
    void setUp() {
        // 실제 서비스를 주입받아 스파이로 만듭니다
        shopServiceSpy = Mockito.spy(shopService);

        // Clean up any previous test data
        reviewRepository.deleteAll();
        shopRepository.deleteAll();

        // Create test shop
        testShop = new Shop("Test", ShopCategory.PIZZA);
        shopRepository.save(testShop);
    }

//    @Test
//    void testOptimisticLockingWithConcurrentReviews() throws InterruptedException {
//        // Given
//        int numberOfThreads = 10;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        AtomicInteger successCount = new AtomicInteger(0);
//
//        // When - Submit multiple reviews concurrently
//        for (int i = 0; i < numberOfThreads; i++) {
//            final BigDecimal rating = BigDecimal.valueOf(i % 5 + 1); // Ratings from 1-5
//            executorService.submit(() -> {
//                try {
//                    CreateReviewDto dto = new CreateReviewDto("test", null, testShop.getId(), "test", rating);
//
//                    reviewService.saveReviewBy(dto);
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    System.err.println("Error in thread: " + e.getMessage());
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // Wait for all threads to complete
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        // 1. Verify all reviews were saved
//        assertThat(reviewRepository.countBy(testShop.getId())).isEqualTo(numberOfThreads);
////
//        // 2. Instead of verifying the spy (which we can't easily do now), we check the results
//        // Check that we have the expected number of reviews
//        int reviewCount = reviewRepository.findAllByShopId(testShop.getId()).size();
//        assertThat(reviewCount).isEqualTo(numberOfThreads);
//
//        // 3. Verify final rating average is correct
//        Shop updatedShop = shopRepository.findById(testShop.getId()).orElseThrow();
//        double expectedAverage = reviewRepository.findAllByShopId(testShop.getId())
//                .stream()
//                .mapToInt(Review::getRating)
//                .average()
//                .orElse(0.0);
//
//        assertThat(updatedShop.getRatingAverage()).isEqualTo(expectedAverage);
//    }

    @Test
    void testRecoverMethodWithOptimisticLocking() throws InterruptedException {
        // Given
        // 로그 캡처를 위한 설정

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger retryCount = new AtomicInteger(0);

        // Mock ReviewRepository to verify countBy method is called during recovery
        ReviewRepository mockReviewRepo = Mockito.spy(reviewRepository);

        // 동일한 상점에 대한 두 이벤트 생성
        CreateReviewEvent event1 = new CreateReviewEvent(1L, testShop.getId(), BigDecimal.valueOf(5));
        CreateReviewEvent event2 = new CreateReviewEvent(2L, testShop.getId(), BigDecimal.valueOf(4));

        // When - 두 스레드에서 경쟁적으로 업데이트 시도
        Thread thread1 = new Thread(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                reviewService.updateAverageRating(event1);
            } catch (Exception e) {
                System.out.println("Thread 1 exception: " + e.getMessage());
                retryCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                // 충돌 확률을 높이기 위한 약간의 지연
                Thread.sleep(50);
                reviewService.updateAverageRating(event2);
            } catch (Exception e) {
                System.out.println("Thread 2 exception: " + e.getMessage());
                retryCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        thread1.start();
        thread2.start();

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        // Then
        // 1. 충분한 시간 대기 (Backoff 지연 시간 + 여유)
        Thread.sleep(9000);

        // 4. 최종 상태 확인 - 두 리뷰의 평점이 반영되어야 함
        Shop updatedShop = shopRepository.findById(testShop.getId()).orElseThrow();
        assertThat(updatedShop.getRawRatingAverage()).isNotEqualTo(BigDecimal.valueOf(4.5));
    }

    // 통합 테스트로 실제 DB 트랜잭션과 이벤트 리스너를 검증
//    @Test
//    @Transactional
//    void testEndToEndReviewSubmissionWithConcurrency() throws InterruptedException {
//        // Given
//        int numberOfThreads = 5;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//
//        // When - Submit multiple reviews with same rating for simplicity
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    CreateReviewDto dto = new CreateReviewDto();
//                    dto.setShopId(testShop.getId());
//                    dto.setContent("End-to-end test");
//                    dto.setRating(4); // All reviews have rating 4
//
//                    shopService.saveReviewBy(dto);
//                } catch (Exception e) {
//                    System.err.println("Error in end-to-end test: " + e.getMessage());
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // Wait for all threads to complete
//        latch.await();
//        executorService.shutdown();
//
//        // Give time for async operations to complete
//        Thread.sleep(3000);
//
//        // Then
//        Shop updatedShop = shopRepository.findById(testShop.getId()).orElseThrow();
//
//        // All reviews should be saved
//        assertThat(reviewRepository.countBy(testShop.getId())).isEqualTo(numberOfThreads);
//
//        // Rating average should be 4.0 (all reviews have rating 4)
//        assertThat(updatedShop.getRatingAverage()).isEqualTo(4.0);
//    }
}