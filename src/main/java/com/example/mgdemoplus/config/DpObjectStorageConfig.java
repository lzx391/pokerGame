package com.example.mgdemoplus.config;

import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.impl.DpLocalFileObjectStorage;
import com.example.mgdemoplus.storage.impl.DpMinioObjectStorage;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//多态？
@Configuration
public class DpObjectStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "mgdemoplus.minio.enabled", havingValue = "false", matchIfMissing = true)
    public DpObjectStorage dpLocalObjectStorage(
            @Value("${mgdemoplus.images.file-location:file:P:/javaworkspace/DPGameFiles/images/}") String imagesFileLocation,
            @Value("${mgdemoplus.music.file-location:file:P:/javaworkspace/DPGameFiles/music/}") String musicFileLocation,
            @Value("${mgdemoplus.files.file-location:file:P:/javaworkspace/DPGameFiles/other/}") String filesFileLocation) {
        return new DpLocalFileObjectStorage(imagesFileLocation, musicFileLocation, filesFileLocation);
    }

    @Bean
    @ConditionalOnProperty(name = "mgdemoplus.minio.enabled", havingValue = "true")
    public DpObjectStorage dpMinioObjectStorage(MinioClient minioClient, DpMinioProperties properties) {
        return new DpMinioObjectStorage(minioClient, properties.getBucket());
    }
}
