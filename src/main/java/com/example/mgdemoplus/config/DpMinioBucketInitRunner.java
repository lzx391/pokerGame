package com.example.mgdemoplus.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * MinIO 启动时确保 bucket 存在。
 */
@Component
public class DpMinioBucketInitRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DpMinioBucketInitRunner.class);

    private final MinioClient minioClient;
    private final DpMinioProperties properties;

    public DpMinioBucketInitRunner(MinioClient minioClient, DpMinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String bucket = properties.getBucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("minio bucket created: {}", bucket);
        } else {
            log.info("minio bucket ready: {}", bucket);
        }
    }
}
