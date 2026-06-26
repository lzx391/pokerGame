package com.example.mgdemoplus.room.support;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Registers this JVM in Redis so {@code GET /dp/dev/instances} can list live backend ports.
 */
@Component
public class DpDevInstanceRegistry {

    static final String INDEX_KEY = "dp:dev:instances";
    static final String HEARTBEAT_PREFIX = "dp:dev:instance:heartbeat:";

    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final int serverPort;

    private volatile boolean registered;

    public DpDevInstanceRegistry(
            StringRedisTemplate stringRedisTemplate,
            org.springframework.core.env.Environment environment) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.serverPort = environment.getProperty("server.port", Integer.class, 8088);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        register();
    }

    @Scheduled(fixedRate = 10_000)
    public void heartbeat() {
        if (registered) {
            refreshHeartbeat();
        }
    }

    @PreDestroy
    public void unregister() {
        if (!registered) {
            return;
        }
        String port = String.valueOf(serverPort);
        stringRedisTemplate.opsForSet().remove(INDEX_KEY, port);
        stringRedisTemplate.delete(HEARTBEAT_PREFIX + port);
        registered = false;
    }

    public List<Integer> listLiveInstancePorts() {
        Set<String> members = stringRedisTemplate.opsForSet().members(INDEX_KEY);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> live = new ArrayList<>();
        for (String member : members) {
            if (member == null || member.isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(HEARTBEAT_PREFIX + member.trim()))) {
                try {
                    live.add(Integer.parseInt(member.trim()));
                } catch (NumberFormatException ignored) {
                    // skip non-numeric legacy entries
                }
            }
        }
        Collections.sort(live);
        return live;
    }

    private void register() {
        String port = String.valueOf(serverPort);
        stringRedisTemplate.opsForSet().add(INDEX_KEY, port);
        refreshHeartbeat();
        registered = true;
    }

    private void refreshHeartbeat() {
        stringRedisTemplate.opsForValue().set(
                HEARTBEAT_PREFIX + serverPort,
                "1",
                HEARTBEAT_TTL
        );
    }
}
