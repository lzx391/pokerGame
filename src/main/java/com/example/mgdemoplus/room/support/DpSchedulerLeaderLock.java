package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.config.DpInstanceProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Single leader for global room heartbeat / NPC timer across app instances.
 */
@Component
public class DpSchedulerLeaderLock {

    private static final Duration LEADER_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate stringRedisTemplate;
    private final String instanceId;
    private final String token = UUID.randomUUID().toString();
    private volatile boolean leader;

    public DpSchedulerLeaderLock(StringRedisTemplate stringRedisTemplate, DpInstanceProperties instanceProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.instanceId = instanceProperties.getInstanceId();
    }

    public boolean tryBecomeOrRenewLeader() {
        String key = DpRoomRedisKeys.HEARTBEAT_LEADER;
        if (leader) {
            String current = stringRedisTemplate.opsForValue().get(key);
            if (token.equals(current)) {
                stringRedisTemplate.expire(key, LEADER_TTL);
                return true;
            }
            leader = false;
        }
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, token, LEADER_TTL);
        if (Boolean.TRUE.equals(ok)) {
            leader = true;
            return true;
        }
        String current = stringRedisTemplate.opsForValue().get(key);
        if (token.equals(current)) {
            stringRedisTemplate.expire(key, LEADER_TTL);
            leader = true;
            return true;
        }
        return false;
    }

    public boolean isLeader() {
        return leader;
    }

    public String instanceId() {
        return instanceId;
    }
}
