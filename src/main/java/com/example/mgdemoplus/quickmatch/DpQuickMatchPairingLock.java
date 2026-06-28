package com.example.mgdemoplus.quickmatch;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Global quick-match pairing lock: {@code SET dp:lock:qm:pairing token NX EX}.
 */
@Component
public class DpQuickMatchPairingLock {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int LOCK_RETRY_MS = 25;
    private static final int LOCK_MAX_WAIT_MS = 5_000;

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public DpQuickMatchPairingLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** Non-blocking acquire for {@code attemptQuickMatchPairing}. */
    public String tryAcquire() {
        String token = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(QuickMatchRedisKeys.PAIRING_LOCK, token, LOCK_TTL);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /** Blocking acquire with retry for REST join paths. */
    public String acquireWithRetry() {
        long deadline = System.currentTimeMillis() + LOCK_MAX_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            String token = tryAcquire();
            if (token != null) {
                return token;
            }
            try {
                Thread.sleep(LOCK_RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public void release(String token) {
        if (token == null) {
            return;
        }
        stringRedisTemplate.execute(
                RELEASE_SCRIPT, Collections.singletonList(QuickMatchRedisKeys.PAIRING_LOCK), token);
    }

    public void renew(String token) {
        if (token == null) {
            return;
        }
        String key = QuickMatchRedisKeys.PAIRING_LOCK;
        String current = stringRedisTemplate.opsForValue().get(key);
        if (token.equals(current)) {
            stringRedisTemplate.expire(key, LOCK_TTL);
        }
    }
}
