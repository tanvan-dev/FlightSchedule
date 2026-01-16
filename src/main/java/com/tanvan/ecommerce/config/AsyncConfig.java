package com.tanvan.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Configuration for async background tasks and HTTP client
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Configure custom thread pool executor for @Async methods
     * Prevents thread exhaustion with bounded queue
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);           // Core threads
        executor.setMaxPoolSize(10);           // Max threads
        executor.setQueueCapacity(20);         // Queue size
        executor.setThreadNamePrefix("async-flight-");
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * RestTemplate bean for HTTP calls
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
