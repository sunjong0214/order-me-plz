package com.omp.config;

import com.omp.shop.ReviewStatsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ExecutorProperties.class, ReviewStatsProperties.class})
public class OmpPropertiesConfig {
}
