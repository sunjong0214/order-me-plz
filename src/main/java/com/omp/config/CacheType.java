package com.omp.config;

public enum CacheType {
    // 가게 목록: 5분. 영업 상태·가게 정보·새 가게는 변경 시 무효화(ShopService)하므로 만료 시간은 평점·리뷰 수의 허용 지연이다.
    // 1-1의 "수 분" 허용 안에서 서비스 규모 재조회 부하(키 수 ÷ 만료 시간)를 60초 대비 1/5로 줄이는 값.
    // 근거: benchmark/README.md 2절 "캐시: 가게 목록 만료 시간 5분" (이전 값 24시간은 근거가 없었다)
    SHOPS("shops", 5 * 60, 10000),
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