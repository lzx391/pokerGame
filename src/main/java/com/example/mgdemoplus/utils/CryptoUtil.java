package com.example.mgdemoplus.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 摘要、HMAC、Base64、随机串等常用密码学工具（与 {@link JwtUtil} 会话 JWT 分离）。
 * <p>
 * <b>选用指引（按需调用即可）：</b>
 * </p>
 * <ul>
 *   <li><b>用户口令落库（当前业务）</b>：{@link #md5HexUtf8(String)} — 与 {@code dp_user.password} 约定一致。</li>
 *   <li><b>文件/内容指纹、非口令场景</b>：{@link #sha256HexUtf8(String)} 或 {@link #sha512HexUtf8(String)} — 比 MD5 更适合作校验和。</li>
 *   <li><b>接口签名、防篡改</b>：{@link #hmacSha256Hex(String, String)} — 需双方共持密钥。</li>
 *   <li><b>二进制安全传输/存储（非加密）</b>：{@link #base64Encode(byte[])} / {@link #base64Decode(String)}。</li>
 *   <li><b>重置令牌、邀请码等随机串</b>：{@link #secureRandomHex(int)}。</li>
 * </ul>
 */
public final class CryptoUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {
    }

    // -------------------------------------------------------------------------
    // 单向摘要（不可逆；验证时：对输入再算一遍与库中比对）
    // -------------------------------------------------------------------------

    /**
     * <b>MD5(UTF-8)</b>，输出 32 位小写十六进制。
     * <p>
     * <b>用途</b>：本项目用户密码入库字段与历史数据约定。<br>
     * <b>注意</b>：MD5 已不推荐用于新系统的口令存储；新需求可评估 {@link #sha256HexUtf8(String)} 或 bcrypt 等。
     * </p>
     *
     * @param plainText 明文；{@code null} 按空串
     */
    public static String md5HexUtf8(String plainText) {
        return digestHex("MD5", plainText);
    }

    /**
     * <b>SHA-256(UTF-8)</b>，输出 64 位小写十六进制。
     * <p>
     * <b>用途</b>：内容校验、指纹、日志防篡改键；<b>不要</b>与当前 {@code dp_user.password}（MD5）混用除非做迁移。
     * </p>
     *
     * @param plainText 明文；{@code null} 按空串
     */
    public static String sha256HexUtf8(String plainText) {
        return digestHex("SHA-256", plainText);
    }

    /**
     * <b>SHA-512(UTF-8)</b>，输出 128 位小写十六进制。
     * <p>
     * <b>用途</b>：与 SHA-256 类似，摘要更长、碰撞更难；计算略重。
     * </p>
     *
     * @param plainText 明文；{@code null} 按空串
     */
    public static String sha512HexUtf8(String plainText) {
        return digestHex("SHA-512", plainText);
    }

    private static String digestHex(String algorithm, String plainText) {
        String raw = plainText == null ? "" : plainText;
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return bytesToLowerHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " algorithm not available", e);
        }
    }

    // -------------------------------------------------------------------------
    // 消息认证码（需密钥；用于签名而非「加密存储口令」）
    // -------------------------------------------------------------------------

    /**
     * <b>HMAC-SHA256</b>，密钥与消息均为 UTF-8，输出小写十六进制。
     * <p>
     * <b>用途</b>：Webhook 签名、开放 API 请求校验、与第三方约定「共享密钥 + 正文」防篡改。<br>
     * <b>注意</b>：密钥需保密传输与存储；与 JWT 所用密钥无必然关系。
     * </p>
     *
     * @param secretUtf8 共享密钥；{@code null} 按空串
     * @param messageUtf8 待认证消息；{@code null} 按空串
     */
    public static String hmacSha256Hex(String secretUtf8, String messageUtf8) {
        String secret = secretUtf8 == null ? "" : secretUtf8;
        String message = messageUtf8 == null ? "" : messageUtf8;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return bytesToLowerHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Base64（编码，不是加密；任何人可解码）
    // -------------------------------------------------------------------------

    /**
     * 字节数组 → Base64 字符串（URL 安全场景请用 {@link #base64UrlEncode(byte[])}）。
     */
    public static String base64Encode(byte[] raw) {
        if (raw == null) {
            return Base64.getEncoder().encodeToString(new byte[0]);
        }
        return Base64.getEncoder().encodeToString(raw);
    }

    /**
     * Base64 字符串 → 字节数组；非法字符会抛 {@link IllegalArgumentException}。
     */
    public static byte[] base64Decode(String base64Text) {
        if (base64Text == null || base64Text.isEmpty()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(base64Text);
    }

    /**
     * UTF-8 文本 → Base64（便于传短文本/JSON）。
     */
    public static String base64EncodeUtf8(String text) {
        String t = text == null ? "" : text;
        return base64Encode(t.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64 → UTF-8 文本。
     */
    public static String base64DecodeToUtf8String(String base64Text) {
        return new String(base64Decode(base64Text), StandardCharsets.UTF_8);
    }

    /**
     * Base64（URL 安全、无换行）— 适合放在 query / 片段里。
     */
    public static String base64UrlEncode(byte[] raw) {
        if (raw == null) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[0]);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * 解析 {@link #base64UrlEncode(byte[])} 的结果。
     */
    public static byte[] base64UrlDecode(String base64UrlText) {
        if (base64UrlText == null || base64UrlText.isEmpty()) {
            return new byte[0];
        }
        return Base64.getUrlDecoder().decode(base64UrlText);
    }

    // -------------------------------------------------------------------------
    // 随机串（用于令牌、盐值占位等；真正口令哈希请配合专门 KDF）
    // -------------------------------------------------------------------------

    /**
     * 加密强度随机字节转小写十六进制，长度 = {@code byteCount * 2} 个字符。
     * <p>
     * <b>用途</b>：重置密码令牌、一次性 nonce、邀请码原料等。<br>
     * <b>示例</b>：{@code secureRandomHex(16)} → 32 个 hex 字符。
     * </p>
     *
     * @param byteCount 随机字节数，须 ≥ 0
     */
    public static String secureRandomHex(int byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount must be >= 0");
        }
        if (byteCount == 0) {
            return "";
        }
        byte[] buf = new byte[byteCount];
        SECURE_RANDOM.nextBytes(buf);
        return bytesToLowerHex(buf);
    }

    private static String bytesToLowerHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
