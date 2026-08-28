package com.omp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

@Configuration
public class SseThreadPoolConfig {

    @Bean(name = "sseTaskExecutor")
    public Executor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("SseTaskExecutor-");
        // 기본 AbortPolicy면 포화 시 runAsync가 예외를 던져 AsyncOrderManager.remove()가
        // 실행되지 않고 맵에 엔트리가 누적된다(메모리 누수).
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        return executor;
    }
}
