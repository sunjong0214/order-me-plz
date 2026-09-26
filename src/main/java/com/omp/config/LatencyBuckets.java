package com.omp.config;

import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * 벤치마크용 지연 Timer의 공통 버킷.
 * serviceLevelObjectives 로 지정한 경계가 Prometheus 누적 히스토그램(_bucket{le=...})으로 노출된다.
 * 누적값이므로 두 시점의 스냅샷 차이로 임의 구간(예: 스파이크 60초)의 분포와 "30초 이내 완료 비율"을 정확히 셀 수 있다.
 * 20~30초 부근을 촘촘히 둔 것은 저장 완료 한도(p99 ≤ 30초) 판정용이다.
 */
public final class LatencyBuckets {
    private static final Duration[] SLO = {
            Duration.ofMillis(5), Duration.ofMillis(10), Duration.ofMillis(25), Duration.ofMillis(50),
            Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500),
            Duration.ofSeconds(1), Duration.ofMillis(2500), Duration.ofSeconds(5), Duration.ofSeconds(10),
            Duration.ofSeconds(15), Duration.ofSeconds(20), Duration.ofSeconds(25), Duration.ofSeconds(30),
            Duration.ofSeconds(45), Duration.ofSeconds(60), Duration.ofSeconds(120)
    };

    private LatencyBuckets() {}

    public static Timer.Builder timer(String name, String description) {
        return Timer.builder(name).description(description).serviceLevelObjectives(SLO);
    }
}
