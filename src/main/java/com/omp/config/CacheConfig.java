package com.omp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    // 만료 시간의 0~10%를 무작위로 더한다 (가게 목록 5분이면 0~30초)
    private static final double JITTER_RATIO = 0.1;

    /**
     * 트랜잭션 인식 캐시: 트랜잭션 안의 무효화(@CacheEvict)는 커밋 후에 실행된다.
     * 커밋 전에 비우면, 그 사이 들어온 조회가 아직 커밋되지 않은 옛 값을 다시 캐시에 넣을 수 있다.
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<CaffeineCache> caches = Arrays.stream(CacheType.values())
                .map(cache -> new CaffeineCache(cache.cacheName, Caffeine.newBuilder().recordStats()
                        .expireAfter(new JitteredExpiry(Duration.ofSeconds(cache.expiredAfterWrite), JITTER_RATIO))
                        .maximumSize(cache.maximumSize)
                        .build()))
                .toList();
        cacheManager.setCaches(caches);
        cacheManager.initializeCaches();
        return new TransactionAwareCacheManagerProxy(cacheManager);
    }
}
