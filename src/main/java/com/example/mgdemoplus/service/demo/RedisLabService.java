package com.example.mgdemoplus.service.demo;

import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 本地实验用：演示 StringRedisTemplate 常见用法。所有键自动加前缀 {@value #KEY_PREFIX}，避免误删业务数据。
 */
@Service
public class RedisLabService {

    static final String KEY_PREFIX = "mgdemo:lab:";

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLabService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        String t = raw.trim();
        if (!SAFE_KEY.matcher(t).matches()) {
            throw new IllegalArgumentException("key 仅允许字母数字下划线连字符，长度 1～64");
        }
        return KEY_PREFIX + t;
    }

    /** 写入当前时间戳并读回，用于确认 Redis 连通。 */
    public Map<String, Object> ping() {
        String key = KEY_PREFIX + "ping";
        String stamp = Instant.now().toString();
        stringRedisTemplate.opsForValue().set(key, stamp, 1, TimeUnit.MINUTES);
        String got = stringRedisTemplate.opsForValue().get(key);
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("written", stamp);
        m.put("readBack", got);
        return m;
    }

    public Map<String, Object> setKv(String shortKey, String value, Integer ttlSeconds) {
        String key = normalizeKey(shortKey);
        if (value == null) {
            throw new IllegalArgumentException("value 不能为空");
        }
        if (ttlSeconds != null && ttlSeconds > 0) {
            stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(key, value);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("value", value);
        m.put("ttlSeconds", ttlSeconds);
        return m;
    }

    public Map<String, Object> getKv(String shortKey) {
        String key = normalizeKey(shortKey);
        String v = stringRedisTemplate.opsForValue().get(key);
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("value", v);
        m.put("exists", v != null);
        return m;
    }

    public Map<String, Object> increment(String shortKey, long delta) {
        String key = normalizeKey(shortKey);
        assertKeyCompatibleWithIncr(key);
        try {
            Long after = stringRedisTemplate.opsForValue().increment(key, delta);
            Map<String, Object> m = new HashMap<>();
            m.put("key", key);
            m.put("delta", delta);
            m.put("after", after);
            return m;
        } catch (RedisSystemException e) {
            throw new IllegalArgumentException(
                    "INCR 失败：该 key 可能存过非整数字符串，或类型不是字符串。请先调用 GET /demo/redis/del?key=你的短key 删掉再试，或换一个没用过 set 的 key。",
                    e);
        }
    }

    /**
     * Redis 的 INCR 要求：不存在，或 STRING 且值为整数文本。若先用 /set 写过英文等，会报错。
     */
    private void assertKeyCompatibleWithIncr(String fullKey) {
        DataType dt = stringRedisTemplate.type(fullKey);
        if (dt == null || DataType.NONE.equals(dt)) {
            return;
        }
        if (!DataType.STRING.equals(dt)) {
            throw new IllegalArgumentException(
                    "该 key 在 Redis 里不是「字符串」类型，无法 INCR。请先 /demo/redis/del 删掉或换 key。");
        }
        String v = stringRedisTemplate.opsForValue().get(fullKey);
        if (v != null && !v.trim().matches("-?\\d+")) {
            throw new IllegalArgumentException(
                    "该 key 里现在不是整数（例如之前用 /set 存过文字）。请先 /demo/redis/del?key=... 删除后再 incr，或换一个新 key。");
        }
    }

    public Map<String, Object> delete(String shortKey) {
        String key = normalizeKey(shortKey);
        Boolean deleted = stringRedisTemplate.delete(key);
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("deleted", Boolean.TRUE.equals(deleted));
        return m;
    }

    public Map<String, Object> setIfAbsent(String shortKey, String value) {
        String key = normalizeKey(shortKey);
        if (value == null) {
            throw new IllegalArgumentException("value 不能为空");
        }
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, value);
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("value", value);
        m.put("setIfAbsent", Boolean.TRUE.equals(ok));
        return m;
    }
}
