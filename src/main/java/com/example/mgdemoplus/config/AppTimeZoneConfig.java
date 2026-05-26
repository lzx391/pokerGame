package com.example.mgdemoplus.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Align JVM default zone with 北京时间 (Asia/Shanghai, UTC+8). Docker JRE images often default to UTC,
 * which makes {@link java.time.LocalDateTime#now()} and naive {@code DATETIME} columns look 8h behind
 * for UTC+8 users when the frontend displays the ISO string as-is.
 */
@Configuration
public class AppTimeZoneConfig {

    @Value("${mgdemoplus.time-zone:Asia/Shanghai}")
    private String timeZoneId;

    @PostConstruct
    void applyDefaultTimeZone() {
        ZoneId zone = ZoneId.of(timeZoneId);
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
    }
}
