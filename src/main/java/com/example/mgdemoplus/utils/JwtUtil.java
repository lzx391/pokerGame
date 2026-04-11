package com.example.mgdemoplus.utils;



import io.jsonwebtoken.Claims;

import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;



import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;

import java.util.Date;


public class JwtUtil {
    /** 32 个 ASCII 字符 = 32 字节 = 256 位，满足 HS256 */
    private static final String SECRET = "abcdefghhgfedcbaabcdefghhgfedcba";
    /**
     * 必须用 Keys 从字节生成 SecretKey。若把 String 直接传给 signWith(alg, String)，

     * JJWT 0.11+ 会按 Base64 解码该字符串，32 个 Base64 字符解出来只有 24 字节 → 192 位，会报 WeakKeyException。
     */
    /**
     * 从字节生成 SecretKey。若把 String 直接传给 signWith(alg, String)，
     * JJWT 0.11+ 会按 Base64 解码该字符串，32 个 Base64 字符解出来只有 24 字节 → 192 位，会报 WeakKeyException。
     */
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
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


