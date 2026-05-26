package com.example.mgdemoplus.security;

/**
 * 与 {@link com.example.mgdemoplus.config.SecurityConfig}、{@link JwtAuthenticationFilter} 共用：
 * 这些路径不校验 JWT（登录、静态资源、WebSocket 握手等）。
 */
public final class JwtSecurityConstants {

    private JwtSecurityConstants() {
    }

    public static final String[] PERMIT_ALL = {
            "/dpUser/loginProfile",
            "/dpUser/registerUser",
            "/error",
            "/ws/**",
            "/images/**",
            "/music/**",
            "/files/**",
            "/",
            "/index.html",
            "/favicon.ico",
            "/fonts/**",
            // 大厅列表、房间快照轮询：未登录可访问（分享链接、旁观）；其余 /dpRoom/** 需 JWT
            "/dpRoom/getNowRoom",
            "/dpRoom/getAllRooms2",
            // "/dpRoom/publicRooms",
            // "/dpRoom/publicRooms/query",
            "/dp/presence/site-heartbeat/config",
            // SSE 长连接：REQUEST + ASYNC 分派均不强制 authenticated()，身份由 JwtAuthenticationFilter ?token= 写入
            // "/dp/social/stream",
            "/dpMusic/list",
            "/dpDownload/list",
            "/dp/leaderboard/weekly/hand",
            "/dp/leaderboard/weekly/room",
            // Redis 本地实验接口（仅学习用；上线前可删或改需登录）
    };
}
