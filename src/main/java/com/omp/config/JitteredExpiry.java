package com.omp.config;

import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.checkerframework.checker.index.qual.NonNegative;

/**
 * 생성 시점 기준 만료 = 기본 만료 시간 + [0, 기본값 × jitterRatio) 무작위.
 * 같은 순간에 채워진 여러 키(재시작·배포 직후)가 한꺼번에 만료되어 재조회가 몰리는 것을 흩는다.
 * 한 키에 요청이 몰리는 경우는 Jitter로 막을 수 없으므로 @Cacheable(sync = true)가 따로 맡는다.
 * 갱신·조회는 만료 시각을 바꾸지 않는다.
 */
public final class JitteredExpiry implements Expiry<Object, Object> {
    private final long baseNanos;
    private final long jitterBoundNanos;

    public JitteredExpiry(Duration base, double jitterRatio) {
        this.baseNanos = base.toNanos();
        this.jitterBoundNanos = Math.max(1L, (long) (baseNanos * jitterRatio));
    }

    @Override
    public long expireAfterCreate(Object key, Object value, long currentTime) {
        return baseNanos + ThreadLocalRandom.current().nextLong(jitterBoundNanos);
    }

    @Override
    public long expireAfterUpdate(Object key, Object value, long currentTime, @NonNegative long currentDuration) {
        return currentDuration;
    }

    @Override
    public long expireAfterRead(Object key, Object value, long currentTime, @NonNegative long currentDuration) {
        return currentDuration;
    }
}
