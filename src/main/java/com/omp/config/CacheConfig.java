package com.omp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.index.qual.NonNegative;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<CaffeineCache> caches = Arrays.stream(CacheType.values())
                .map(cache -> new CaffeineCache(cache.cacheName, Caffeine.newBuilder().recordStats()
                        .expireAfter(new Expiry<>() {
                            @Override
                            public long expireAfterCreate(Object key, Object value, long currentTime) {
                                long baseExpiry = TimeUnit.SECONDS.toNanos(cache.expiredAfterWrite);
                                long jitter = ThreadLocalRandom.current().nextLong(0, TimeUnit.SECONDS.toNanos(3));
                                return baseExpiry + jitter;
                            }

                            @Override
                            public long expireAfterUpdate(Object key, Object value, long currentTime,
                                                          @NonNegative long currentDuration) {
                                return currentDuration;
                            }

                            @Override
                            public long expireAfterRead(Object key, Object value, long currentTime,
                                                        @NonNegative long currentDuration) {
                                return currentDuration;
                            }
                        })
                        .maximumSize(cache.maximumSize)
                        .build()))
                .toList();
        cacheManager.setCaches(caches);
        return cacheManager;
    }
}
