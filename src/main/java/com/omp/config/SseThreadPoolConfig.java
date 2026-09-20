package com.omp.config;

import com.omp.config.ExecutorProperties.Pool;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class SseThreadPoolConfig {
    private final ExecutorProperties props;

    @Bean(name = "sseTaskExecutor")
    public Executor sseTaskExecutor() {
        Pool pool = props.getSse();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCore());
        executor.setMaxPoolSize(pool.getMax());
        executor.setQueueCapacity(pool.getQueue());
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("SseTaskExecutor-");
        // 여기서는 CallerRunsPolicy를 유지한다. SSE 전송 작업은 트랜잭션 밖(컨트롤러·워커 완료 콜백)에서 제출되므로
        // 호출 스레드 인라인 실행이 커밋 유실을 일으키지 않고, 거절하면 클라이언트가 타임아웃까지 알림을 못 받는다.
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        return executor;
    }
}
