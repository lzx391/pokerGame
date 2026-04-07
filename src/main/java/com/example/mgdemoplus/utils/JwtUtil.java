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
    private static final long EXPIRATION_TIME = 1000 * 60; // 1 minute
    public static String generateToken(String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 10 * EXPIRATION_TIME);//十分钟期限
        return Jwts.builder()
                .header().add("type", "JWT").and()
                .subject(username)
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
}


