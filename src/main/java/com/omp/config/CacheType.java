package com.omp.config;

public enum CacheType {
    SHOPS("shops", 24 * 60 * 60, 10000),
    REVIEWS("reviews", 5 * 60, 10000);

    CacheType(String cacheName, int expiredAfterWrite, int maximumSize) {
        this.cacheName = cacheName;
        this.expiredAfterWrite = expiredAfterWrite;
        this.maximumSize = maximumSize;
    }

    final String cacheName;
    final int expiredAfterWrite;
    final int maximumSize;
}