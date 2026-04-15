package com.example.mgdemoplus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多节点时每个游戏实例不同；单节点使用默认值即可。
 * <p>
 * {@code publicWsUrl} 为方案二「直连节点」的完整 WebSocket URL（需含路径，如 /ws/dp-game）。
 * FRP 穿透时填外网可访问地址。
 */
@ConfigurationProperties(prefix = "dp.game")
public class DpGameInstanceProperties {

    /**
     * 本实例唯一 ID，可与主机名、端口或 K8s Pod 名一致。
     */
    private String shardId = "local-1";

    /**
     * 客户端连接本实例游戏推送的完整 ws/wss URL。
     */
    private String publicWsUrl = "ws://127.0.0.1:8088/ws/dp-game";

    /**
     * Redis 中房间路由缓存 TTL（秒）。
     */
    private int routeRedisTtlSeconds = 120;

    /**
     * 若为 true：{@code /ws/dp-game} 握手须带有效 JWT（query {@code token=}），与 HTTP 使用同一 {@code mgdemoplus.jwt.secret}。
     */
    private boolean requireJwtForDpGameWs = false;

    public String getShardId() {
        return shardId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }

    public String getPublicWsUrl() {
        return publicWsUrl;
    }

    public void setPublicWsUrl(String publicWsUrl) {
        this.publicWsUrl = publicWsUrl;
    }

    public int getRouteRedisTtlSeconds() {
        return routeRedisTtlSeconds;
    }

    public void setRouteRedisTtlSeconds(int routeRedisTtlSeconds) {
        this.routeRedisTtlSeconds = routeRedisTtlSeconds;
    }

    public boolean isRequireJwtForDpGameWs() {
        return requireJwtForDpGameWs;
    }

    public void setRequireJwtForDpGameWs(boolean requireJwtForDpGameWs) {
        this.requireJwtForDpGameWs = requireJwtForDpGameWs;
    }
}
