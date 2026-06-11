package com.example.mgdemoplus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置（前缀 {@code mgdemoplus.minio}）。
 * {@code enabled=false}（默认）时不创建 MinioClient，文件仍走本地磁盘。
 */
@ConfigurationProperties(prefix = "mgdemoplus.minio")
public class DpMinioProperties {

    private boolean enabled = false;
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "mgdemo";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
