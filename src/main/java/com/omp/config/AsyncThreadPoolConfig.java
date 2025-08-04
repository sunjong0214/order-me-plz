package com.omp.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncThreadPoolConfig {

    @Bean(name = "insertTaskExecutor")
    public Executor insertTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(10);
        taskExecutor.setMaxPoolSize(30);
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("AsyncTask-");
        taskExecutor.setRejectedExecutionHandler(new CallerRunsPolicy());
        return taskExecutor;
    }
}
