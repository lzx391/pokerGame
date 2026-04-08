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
            "/dpUser/loginUser",
            "/error",
            "/ws/**",
            "/images/**",
            "/music/**",
            "/",
            "/index.html",
            "/favicon.ico",
            // 大厅列表、房间快照轮询：未登录可访问（分享链接、旁观）；其余 /dpRoom/** 需 JWT
            "/dpRoom/getNowRoom",
            "/dpRoom/getAllRooms2",
            "/dpMusic/list",
    };
}
