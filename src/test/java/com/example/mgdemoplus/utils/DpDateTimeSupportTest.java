package com.example.mgdemoplus.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DpDateTimeSupportTest {

    @Test
    void toEpochMilli_roundTripsViaSystemDefaultZone() {
        LocalDateTime dt = LocalDateTime.of(2026, 5, 26, 12, 0, 0, 123_000_000);
        Long ms = DpDateTimeSupport.toEpochMilli(dt);
        assertThat(ms).isNotNull();
        LocalDateTime back =
                java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime();
        assertThat(back).isEqualTo(dt);
    }

    @Test
    void toEpochMilli_nullReturnsNull() {
        assertThat(DpDateTimeSupport.toEpochMilli(null)).isNull();
    }
}
