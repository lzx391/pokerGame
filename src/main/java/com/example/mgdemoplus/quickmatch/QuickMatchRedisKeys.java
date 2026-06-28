package com.example.mgdemoplus.quickmatch;

/**
 * Redis key / channel names for distributed quick-match runtime (P0 multi-instance).
 */
public final class QuickMatchRedisKeys {

    /** FIFO wait queue (LIST of JSON {@link com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry}). */
    public static final String WAIT_QUEUE = "dp:qm:wait";

    /** Global pairing / queue mutation lock. */
    public static final String PAIRING_LOCK = "dp:lock:qm:pairing";

    /** Cross-instance quick-match WebSocket fan-out. */
    public static final String EVENTS_CHANNEL = "dp:qm:events";

    /** Reverse map: roomId → shortage (1..maxSeatCount). */
    public static final String JOIN_ROOM2BUCKET = "dp:qm:join:room2bucket";

    /** Non-empty vacancy buckets; member and score are both shortage. */
    public static final String JOIN_ACTIVE_BUCKETS = "dp:qm:join:active_buckets";

    /** Prefix for per-shortage room ZSETs (score fixed 0 for lex order). */
    public static final String JOIN_BUCKET_PREFIX = "dp:qm:join:bucket:";

    public static String joinBucketKey(int shortage) {
        return JOIN_BUCKET_PREFIX + shortage;
    }

    private QuickMatchRedisKeys() {
    }
}
