package com.example.mgdemoplus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class DpSettlePersistenceExecutorConfig {

    @Bean(name = "dpSettlePersistExecutor")
    public Executor dpSettlePersistExecutor(
            @Value("${mgdemoplus.settle-persist.queue-capacity:256}") int queueCapacity) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("dp-settle-persist-");
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(Math.max(16, queueCapacity));
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(3);
        ex.initialize();
        return ex;
    }
}
