package com.example.mgdemoplus.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 将库内 naive {@code DATETIME}（按 JVM 默认时区解释，见 {@link com.example.mgdemoplus.config.AppTimeZoneConfig}）
 * 转为 API 使用的 epoch 毫秒。
 */
public final class DpDateTimeSupport {

    private DpDateTimeSupport() {
    }

    public static Long toEpochMilli(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
