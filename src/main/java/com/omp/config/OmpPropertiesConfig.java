package com.omp.config;

import com.omp.order.SyncOrderProperties;
import com.omp.order.async.AsyncOrderProperties;
import com.omp.shop.ReviewStatsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ExecutorProperties.class, ReviewStatsProperties.class, AsyncOrderProperties.class,
        SyncOrderProperties.class})
public class OmpPropertiesConfig {
}
