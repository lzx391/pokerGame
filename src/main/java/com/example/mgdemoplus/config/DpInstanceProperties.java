package com.example.mgdemoplus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Logical instance marker for multi-node dev (e.g. port {@code 8088}).
 */
@Component
@ConfigurationProperties(prefix = "mgdemoplus")
public class DpInstanceProperties {

    /** Console / log marker; should match {@code server.port} per JVM. */
    private String instanceId = "8088";

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId == null || instanceId.isBlank() ? "8088" : instanceId.trim();
    }

    public boolean isRedisRoomStorage() {
        return true;
    }
}
