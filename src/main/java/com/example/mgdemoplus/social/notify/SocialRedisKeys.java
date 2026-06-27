package com.example.mgdemoplus.social.notify;

/**
 * Redis channel names for distributed social SSE fan-out (P1 multi-instance).
 */
public final class SocialRedisKeys {

    public static final String EVENTS_CHANNEL = "dp:social:events";

    private SocialRedisKeys() {
    }
}
