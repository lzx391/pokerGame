package com.example.mgdemoplus.presence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DpSitePresenceServiceTest {

    @Test
    void touch_then_withinTtl_online() {
        DpSitePresenceService svc = new DpSitePresenceService(10_000);
        svc.touchSiteHeartbeat(42);
        long now = System.currentTimeMillis();
        assertThat(svc.isOnlineSite(42, now)).isTrue();
        assertThat(svc.isOnlineSite(42, now + 9_999)).isTrue();
    }

    @Test
    void noRecord_offline() {
        DpSitePresenceService svc = new DpSitePresenceService(60_000);
        assertThat(svc.isOnlineSite(7)).isFalse();
    }

    @Test
    void afterTtl_offline() {
        DpSitePresenceService svc = new DpSitePresenceService(1_000);
        svc.touchSiteHeartbeat(1);
        long upper = System.currentTimeMillis();
        assertThat(svc.isOnlineSite(1, upper + 999)).isTrue();
        assertThat(svc.isOnlineSite(1, upper + 2_000)).isFalse();
    }

    @Test
    void ttlMs_clamped_positive() {
        DpSitePresenceService svc = new DpSitePresenceService(0);
        assertThat(svc.getTtlMs()).isEqualTo(1L);
    }
}
