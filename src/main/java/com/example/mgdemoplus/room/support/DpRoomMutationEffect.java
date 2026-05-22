package com.example.mgdemoplus.room.support;

/**
 * Side effects to run after an in-memory room mutation (index and/or lobby DB summary).
 */
public enum DpRoomMutationEffect {
    /** Refresh {@link com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex} only. */
    INDEX,
    /** Upsert {@code dp_room_lobby} when summary fingerprint changed. */
    LOBBY,
    /** Index refresh then conditional lobby upsert. */
    BOTH
}
