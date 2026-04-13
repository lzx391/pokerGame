package com.example.mgdemoplus.utils;



import io.jsonwebtoken.Claims;

import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;



import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;

import java.util.Date;


public class JwtUtil {
    /**
     * HS256 需要至少 256 位密钥。优先使用环境变量 {@code JWT_SECRET}（UTF-8 编码后至少 32 字节），
     * 未设置或过短时使用下方仅适用于本地开发的默认值；生产环境务必设置 {@code JWT_SECRET}，勿提交到 Git。
     */
    private static final String DEV_FALLBACK_SECRET = "abcdefghhgfedcbaabcdefghhgfedcba";

    private static SecretKey buildHmacSha256Key() {
        String fromEnv = EnvHelper.getenvOrProperty("JWT_SECRET");
        String raw = (fromEnv != null && !fromEnv.isBlank()) ? fromEnv.trim() : DEV_FALLBACK_SECRET;
        byte[] utf8 = raw.getBytes(StandardCharsets.UTF_8);
        if (utf8.length < 32) {
            raw = DEV_FALLBACK_SECRET;
            utf8 = raw.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(utf8);
    }

    private static final SecretKey SECRET_KEY = buildHmacSha256Key();
    public static final long EXPIRATION_TIME = 1000 * 60; // 1 minute
    /** 与 {@link #generateToken} 中 {@code expiration} 一致，供 Redis 会话 jti 设置 TTL（秒）。 */
    public static long tokenTtlSeconds() {
        return (24L * 60 * EXPIRATION_TIME) / 1000;
    }

    public static String generateToken(String username,String jti) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 24 *60 * EXPIRATION_TIME);//单位分钟期限
        return Jwts.builder()
                .header().add("type", "JWT").and()
                .subject(username)
                .id(jti)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(SECRET_KEY)
                .compact();
    }

    public static Claims verifyToken(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 HTTP {@code Authorization} 头解析 Bearer token。
     * 约定：{@code Authorization} 值为 {@code Bearer } 前缀加 JWT（前缀大小写不敏感）。
     *
     * @return 纯 JWT 字符串；缺失或非 Bearer 时返回 {@code null}
     */
    public static String stripBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String s = authorizationHeader.trim();
        if (s.length() < 8 || !s.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String t = s.substring(7).trim();
        return t.isEmpty() ? null : t;
    }
}


