package com.example.mgdemoplus.dp.presence;

/**
 * JVM 内存「房内」好友态（单机）：在座或观众为 {@link #IN_GAME}，否则 {@link #IDLE}。
 * 好友列表对外展示时由 {@link com.example.mgdemoplus.service.serviceImpl.dp.DpFriendSocialService#listFriends}
 * 与 {@link com.example.mgdemoplus.service.dp.DpSitePresenceService} 合成，可能为 {@link #OFFLINE}。
 */
public enum DpFriendPresenceState {
    /** 房内未记为在局（缺省）。*/
    IDLE,
    /** 在某一房间内：在座或观众席均算同一态。*/
    IN_GAME,
    /** 不在对局中且站点心跳过期时对外展示（房内态优先）。*/
    OFFLINE,
}
