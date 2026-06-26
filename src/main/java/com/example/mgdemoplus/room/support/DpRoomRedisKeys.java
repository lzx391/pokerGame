package com.example.mgdemoplus.room.support;

/**
 * Redis key / channel names for distributed room runtime (P0 multi-instance).
 */
public final class DpRoomRedisKeys {

    public static final String STATE_PREFIX = "dp:room:state:";
    public static final String REV_PREFIX = "dp:room:rev:";
    public static final String INDEX = "dp:room:index";
    public static final String LOCK_PREFIX = "dp:lock:room:";
    public static final String EVENTS_CHANNEL = "dp:room:events";
    public static final String HEARTBEAT_LEADER = "dp:scheduler:heartbeat:leader";

    private DpRoomRedisKeys() {
    }

    public static String stateKey(String roomId) {
        return STATE_PREFIX + roomId;
    }

    public static String revKey(String roomId) {
        return REV_PREFIX + roomId;
    }

    public static String lockKey(String roomId) {
        return LOCK_PREFIX + roomId;
    }
}
