package com.example.mgdemoplus.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 站点级在线：与房内 {@link DpFriendPresenceService} 心跳解耦；单机
 * {@link ConcurrentHashMap} 记录 {@code last_seen_site}（epoch millis）。
 */
@Service
public class DpSitePresenceService {

    private final ConcurrentHashMap<Integer, Long> lastSeenSiteMillisByUserId = new ConcurrentHashMap<>();

    private final long ttlMs;

    public DpSitePresenceService(
            @Value("${mgdemoplus.dp-site-presence-ttl-ms:90000}") long ttlMs) {
        this.ttlMs = Math.max(1L, ttlMs);
    }

    /** JWT 鉴权后刷新当前用户的站点心跳时间（幂等）。 */
    public void touchSiteHeartbeat(int dpUserId) {
        if (dpUserId <= 0) {
            return;
        }
        lastSeenSiteMillisByUserId.put(dpUserId, System.currentTimeMillis());
    }

    /**
     * {@code now - last_seen_site <= ttlMs} 时视为站点在线；无记录为 false。
     */
    public boolean isOnlineSite(int dpUserId) {
        return isOnlineSite(dpUserId, System.currentTimeMillis());
    }

    boolean isOnlineSite(int dpUserId, long nowMillis) {
        if (dpUserId <= 0) {
            return false;
        }
        Long t = lastSeenSiteMillisByUserId.get(dpUserId);
        if (t == null) {
            return false;
        }
        return nowMillis - t <= ttlMs;
    }

    /** 与 {@link #isOnlineSite(int)} 判定一致；便于客户端对齐退避与重试。 */
    public long getTtlMs() {
        return ttlMs;
    }

    /** 建议轮询间隔：不少于 5s，约为 TTL/3（与「TTL &gt; 2～3×间隔」一致）。 */
    public long getSuggestedHeartbeatIntervalMs() {
        return Math.max(5_000L, ttlMs / 3);
    }
}
