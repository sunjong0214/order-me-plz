package com.omp.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비동기 작업 풀 크기. 벤치마크 회차마다 환경 표에 기록하는 값이므로 코드가 아닌 설정으로 노출한다.
 * 기본값은 application.properties 기본값과 같다(파일럿 P2 확정값: insert core = max = 20, queue 70,000,
 * reviewStats core = max = P/2 = 10). 근거는 benchmark/README.md 2절 "풀 크기 산정".
 */
@ConfigurationProperties(prefix = "omp.executor")
@Getter
@Setter
@NoArgsConstructor
public class ExecutorProperties {
    private Pool insert = new Pool(20, 20, 70000);
    private Pool reviewStats = new Pool(10, 10, 2000);
    private Pool sse = new Pool(10, 30, 200);

    public ExecutorProperties(Pool insert, Pool reviewStats, Pool sse) {
        this.insert = insert;
        this.reviewStats = reviewStats;
        this.sse = sse;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pool {
        private int core;
        private int max;
        private int queue;

        public Pool(int core, int max, int queue) {
            this.core = core;
            this.max = max;
            this.queue = queue;
        }
    }
}
