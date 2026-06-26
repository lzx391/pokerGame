package com.example.mgdemoplus.config;

import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.impl.DpMinioObjectStorage;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DpObjectStorageConfig {

    @Bean
    public DpObjectStorage dpMinioObjectStorage(MinioClient minioClient, DpMinioProperties properties) {
        return new DpMinioObjectStorage(minioClient, properties.getBucket());
    }
}
