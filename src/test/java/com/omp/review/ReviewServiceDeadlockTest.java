package com.omp.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omp.review.dto.CreateReviewDto;
import com.omp.shop.Shop;
import com.omp.shop.ShopCategory;
import com.omp.shop.ShopRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RetryException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReviewServiceDeadlockTest {

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void testNoDeadlockWithConcurrentReviews() throws InterruptedException {
        // 테스트 데이터 준비
        Long shopId = 53L; // 테스트용 shop ID

        // 동시 요청 수
        int numberOfThreads = 10;

        // 모든 스레드가 동시에 시작할 수 있도록 CountDownLatch 사용
        CountDownLatch startLatch = new CountDownLatch(1);

        // 모든 스레드가 완료될 때까지 기다리기 위한 CountDownLatch
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        // 발생한 예외를 저장할 리스트
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // 성공한 요청 수를 추적하기 위한 AtomicInteger
        AtomicInteger successCount = new AtomicInteger(0);

        // 여러 스레드에서 동시에 리뷰 생성 요청
        for (int i = 0; i < numberOfThreads; i++) {
            final BigDecimal rating = BigDecimal.valueOf(5.0); // 1~5 사이의 평점

            new Thread(() -> {
                try {
                    // 모든 스레드가 이 지점에서 대기
                    startLatch.await();

                    // 리뷰 생성 요청
                    CreateReviewDto dto = new CreateReviewDto("title", null, shopId, "detail", rating);

                    // 서비스 메소드 호출
                    reviewService.saveReviewBy(dto);

                    // 성공 카운트 증가
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 모든 종류의 예외를 기록
                    exceptions.add(e);
                    System.out.println("Exception caught: " + e.getClass().getName() + " - " + e.getMessage());
                } finally {
                    // 스레드 완료 알림
                    finishLatch.countDown();
                }
            }).start();
        }
        long start = System.currentTimeMillis();
        // 모든 스레드를 동시에 시작
        startLatch.countDown();

        // 모든 스레드가 완료될 때까지 대기 (최대 30초)
        boolean completed = finishLatch.await(30, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        // 테스트 결과 출력 및 검증
        System.out.println("Test completed: " + completed);
        System.out.println("Success count: " + successCount.get());
        System.out.println("Exception count: " + exceptions.size());

        if (!exceptions.isEmpty()) {
            System.out.println("Exception types:");
            Map<String, Long> exceptionTypes = exceptions.stream()
                    .collect(Collectors.groupingBy(e -> e.getClass().getSimpleName(), Collectors.counting()));
            exceptionTypes.forEach((type, count) -> System.out.println(type + ": " + count));
        }
        System.out.println("completed time = " + (end - start));

        // 모든 요청이 성공했는지 확인
        assertEquals(numberOfThreads, successCount.get(), "일부 요청이 실패했습니다.");
        assertEquals(0, exceptions.size(), "예외가 발생했습니다.");
    }
}