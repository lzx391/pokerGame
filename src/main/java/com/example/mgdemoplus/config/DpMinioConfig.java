package com.example.mgdemoplus.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DpMinioProperties.class)
public class DpMinioConfig {

    @Bean
    @ConditionalOnProperty(name = "mgdemoplus.minio.enabled", havingValue = "true")
    public MinioClient dpMinioClient(DpMinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
