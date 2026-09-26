package com.omp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class JitteredExpiryTest {

    @Test
    void 만료_시간은_기본값에서_기본값의_10퍼센트_사이로_흩어진다() {
        Duration base = Duration.ofMinutes(5);
        JitteredExpiry expiry = new JitteredExpiry(base, 0.1);
        long min = base.toNanos();
        long maxExclusive = min + base.toNanos() / 10;   // 5분 + 30초

        Set<Long> seconds = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long nanos = expiry.expireAfterCreate("key", "value", 0L);
            assertThat(nanos).isGreaterThanOrEqualTo(min).isLessThan(maxExclusive);
            seconds.add(TimeUnit.NANOSECONDS.toSeconds(nanos));
        }
        assertThat(seconds.size()).isGreaterThan(20);   // 0~30초 폭으로 실제로 흩어진다
    }

    @Test
    void 조회와_갱신은_남은_만료_시간을_바꾸지_않는다() {
        JitteredExpiry expiry = new JitteredExpiry(Duration.ofMinutes(5), 0.1);
        assertThat(expiry.expireAfterRead("key", "value", 0L, 1234L)).isEqualTo(1234L);
        assertThat(expiry.expireAfterUpdate("key", "value", 0L, 1234L)).isEqualTo(1234L);
    }
}
