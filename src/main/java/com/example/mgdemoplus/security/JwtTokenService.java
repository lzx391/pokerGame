package com.example.mgdemoplus.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验；密钥仅来自配置 {@code mgdemoplus.jwt.secret}（可由环境变量 {@code JWT_SECRET} 经 application.properties 映射）。
 */
@Component
public class JwtTokenService {

    private static final long EXPIRATION_TIME = 1000 * 60L;
    private static final String DEV_FALLBACK_SECRET = "abcdefghhgfedcbaabcdefghhgfedcba";

    private final SecretKey secretKey;

    public JwtTokenService(@Value("${mgdemoplus.jwt.secret}") String rawSecret) {
        String raw = rawSecret != null ? rawSecret.trim() : "";
        byte[] utf8 = raw.getBytes(StandardCharsets.UTF_8);
        if (utf8.length < 32) {
            raw = DEV_FALLBACK_SECRET;
            utf8 = raw.getBytes(StandardCharsets.UTF_8);
        }
        this.secretKey = Keys.hmacShaKeyFor(utf8);
    }

    /** 与 {@link #generateToken} 中过期时间一致，供 Redis 会话 jti TTL（秒）。 */
    public long tokenTtlSeconds() {
        return (24L * 60 * EXPIRATION_TIME) / 1000;
    }

    public String generateToken(String username, String jti) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 24 * 60 * EXPIRATION_TIME);
        return Jwts.builder()
                .header().add("type", "JWT").and()
                .subject(username)
                .id(jti)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public Claims verifyToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 HTTP {@code Authorization} 头解析 Bearer token。
     *
     * @return 纯 JWT 字符串；缺失或非 Bearer 时返回 {@code null}
     */
    public String stripBearerToken(String authorizationHeader) {
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
