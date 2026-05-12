package com.example.mgdemoplus.dp.presence;

/**
 * JVM 内存好友Presence（单机）：与「是否在任一 DP 房间内（含观众）」对齐。
 *
 * <p>可选扩展 MATCHING 等非房间态时在此处追加枚举值；本轮仅 IDLE / IN_GAME。
 */
public enum DpFriendPresenceState {
    /** 不在任何房间内（也未作为观众列席）。*/
    IDLE,
    /** 在某一 {@link com.example.mgdemoplus.bo.DpRoomBO} 内：在座或观众席均算同一态。*/
    IN_GAME,
}
