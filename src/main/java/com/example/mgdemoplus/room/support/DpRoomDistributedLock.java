package com.example.mgdemoplus.room.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Per-room Redis lock: {@code SET key token NX EX}.
 */
@Component
public class DpRoomDistributedLock {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public DpRoomDistributedLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String tryAcquire(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(DpRoomRedisKeys.lockKey(roomId), token, LOCK_TTL);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void release(String roomId, String token) {
        if (roomId == null || roomId.isEmpty() || token == null) {
            return;
        }
        stringRedisTemplate.execute(RELEASE_SCRIPT,
                Collections.singletonList(DpRoomRedisKeys.lockKey(roomId)), token);
    }

    public void renew(String roomId, String token) {
        if (roomId == null || roomId.isEmpty() || token == null) {
            return;
        }
        String key = DpRoomRedisKeys.lockKey(roomId);
        String current = stringRedisTemplate.opsForValue().get(key);
        if (token.equals(current)) {
            stringRedisTemplate.expire(key, LOCK_TTL);
        }
    }
}
