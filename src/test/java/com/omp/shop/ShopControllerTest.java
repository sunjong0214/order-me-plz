//package com.omp.shop;
//
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.cache.CacheManager;
//import org.springframework.transaction.annotation.Transactional;
//
//@SpringBootTest
//@Slf4j
//@Transactional
//class ShopControllerTest {
//    @Autowired
//    private ShopController shopController;
//
//    @Autowired
//    private ShopRepository shopRepository;
//
//    @Autowired
//    private CacheManager cacheManager;
//    private static final int ITERATIONS = 10;
//    @BeforeEach
//    void setUp() {
//        for (int i = 0; i < 20; i++) {
//            shopRepository.saveAndFlush(new Shop("title" + i, ShopCategory.BEEF));
//        }
//        // 테스트 전 캐시 초기화
//        cacheManager.getCache("shops").clear();
//    }
//
//    @Test
//    void cachePerformanceTest() {
//        // 캐시 없이 첫 요청
//        ExecutorService executorService = Executors.newFixedThreadPool(2);
//        CountDownLatch countDownLatch = new CountDownLatch(2);
//        long totalTimeWithoutCache = 0;
//        for (int i = 0; i < ITERATIONS; i++) {
//            cacheManager.getCache("shops").clear();
//            long start = System.currentTimeMillis();
////            shopController.getShopById(ShopCategory.BEEF, 0L, 10);
//            long end = System.currentTimeMillis();
//            totalTimeWithoutCache += (end - start);
//            System.out.println("totalTimeWithoutCache = " + totalTimeWithoutCache);
//        }
//
//        // 캐시 있는 상태에서 여러 번 호출
////        shopController.getShopById(ShopCategory.BEEF, 0L, 10); // 캐시 채우기
//        long totalTimeWithCache = 0;
//        for (int i = 0; i < ITERATIONS; i++) {
//            long start = System.currentTimeMillis();
////            shopController.getShopById(ShopCategory.FOOD, 0L, 10);
//            long end = System.currentTimeMillis();
//            totalTimeWithCache += (end - start);
//
//        }
//
//        double avgTimeWithoutCache = totalTimeWithoutCache / (double) ITERATIONS;
//        double avgTimeWithCache = totalTimeWithCache / (double) ITERATIONS;
//
//        log.info("캐시 없음 평균: {}ms", avgTimeWithoutCache);
//        log.info("캐시 사용 평균: {}ms", avgTimeWithCache);
//        log.info("성능 향상: {}배", avgTimeWithoutCache / avgTimeWithCache);
//
//        // 단언문
//        assertTrue(avgTimeWithoutCache > avgTimeWithCache);
//        // 의미 있는 성능 향상이 있는지 확인 (예: 적어도 2배 이상 빠름)
//        assertTrue(avgTimeWithoutCache / avgTimeWithCache >= 2.0);
//    }
//}