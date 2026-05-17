package com.example.mgdemoplus.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 进程内 {@code userId -> SseEmitter} 注册表；P0 无跨实例扇出。
 */
@Component
public class SocialSseHub {

    private static final Logger log = LoggerFactory.getLogger(SocialSseHub.class);

    static final int MAX_CONNECTIONS_PER_USER = 3;
    private static final long HEARTBEAT_INTERVAL_SEC = 30L;
    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final ObjectMapper objectMapper;
    private final SocialNotifyPublisher socialNotifyPublisher;
    private final ConcurrentHashMap<Integer, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "social-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public SocialSseHub(ObjectMapper objectMapper, @Lazy SocialNotifyPublisher socialNotifyPublisher) {
        this.objectMapper = objectMapper;
        this.socialNotifyPublisher = socialNotifyPublisher;
        heartbeatScheduler.scheduleAtFixedRate(
                this::onHeartbeatTick,
                HEARTBEAT_INTERVAL_SEC,
                HEARTBEAT_INTERVAL_SEC,
                TimeUnit.SECONDS);
    }

    private void onHeartbeatTick() {
        if (!emittersByUser.isEmpty()) {
            socialNotifyPublisher.expireDueRoomInvitesAndNotify();
        }
        sendHeartbeatsToAll();
    }

    public SseEmitter connect(int userId, SocialNotifyPayload initialPayload) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        register(userId, emitter);
        log.info(
                "[social-sse] connect userId={} emitter={} connectionsForUser={} onlineUserIds={}",
                userId,
                System.identityHashCode(emitter),
                connectionCount(userId),
                onlineUserIdsSnapshot());
        emitter.onCompletion(() -> unregister(userId, emitter, "completion"));
        emitter.onTimeout(() -> unregister(userId, emitter, "timeout"));
        emitter.onError(ex -> unregister(userId, emitter, "error:" + ex.getClass().getSimpleName()));
        try {
            sendNotifyEvent(emitter, initialPayload, "initial");
            log.info(
                    "[social-sse] initial notify sent userId={} mailboxUnread={} friendChatUnreadTotal={}",
                    userId,
                    initialPayload.getMailboxUnread(),
                    initialPayload.getFriendChatUnreadTotal());
        } catch (IOException e) {
            log.warn("[social-sse] initial notify failed userId={} reason={}", userId, e.toString());
            unregister(userId, emitter, "initial-send-failed");
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void broadcastNotify(int userId, SocialNotifyPayload payload) {
        Set<SseEmitter> set = emittersByUser.get(userId);
        int n = set != null ? set.size() : 0;
        if (n == 0) {
            log.warn(
                    "[social-sse] broadcast skipped: no active SSE for userId={} mailboxUnread={} "
                            + "friendChatUnreadTotal={} onlineUserIds={}",
                    userId,
                    payload.getMailboxUnread(),
                    payload.getFriendChatUnreadTotal(),
                    onlineUserIdsSnapshot());
            return;
        }
        log.info(
                "[social-sse] broadcast userId={} connections={} mailboxUnread={} friendChatUnreadTotal={}",
                userId,
                n,
                payload.getMailboxUnread(),
                payload.getFriendChatUnreadTotal());
        int sent = 0;
        for (SseEmitter emitter : set) {
            try {
                sendNotifyEvent(emitter, payload, "broadcast");
                sent++;
            } catch (IOException e) {
                log.warn(
                        "[social-sse] broadcast send failed userId={} emitter={} reason={}",
                        userId,
                        System.identityHashCode(emitter),
                        e.toString());
                unregister(userId, emitter, "broadcast-send-failed");
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // already closed
                }
            }
        }
        log.info("[social-sse] broadcast done userId={} sentTo={}/{}", userId, sent, n);
    }

    private void register(int userId, SseEmitter emitter) {
        Set<SseEmitter> set =
                emittersByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        synchronized (set) {
            while (set.size() >= MAX_CONNECTIONS_PER_USER) {
                Iterator<SseEmitter> it = set.iterator();
                if (!it.hasNext()) {
                    break;
                }
                SseEmitter oldest = it.next();
                it.remove();
                try {
                    oldest.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
            set.add(emitter);
        }
    }

    private void unregister(int userId, SseEmitter emitter, String reason) {
        Set<SseEmitter> set = emittersByUser.get(userId);
        if (set == null) {
            return;
        }
        boolean removed = set.remove(emitter);
        if (set.isEmpty()) {
            emittersByUser.remove(userId, set);
        }
        if (removed) {
            log.info(
                    "[social-sse] disconnect userId={} emitter={} reason={} remaining={} onlineUserIds={}",
                    userId,
                    System.identityHashCode(emitter),
                    reason,
                    connectionCount(userId),
                    onlineUserIdsSnapshot());
        }
    }

    private void sendNotifyEvent(SseEmitter emitter, SocialNotifyPayload payload, String phase)
            throws IOException {
        String json = objectMapper.writeValueAsString(payload.toDataMap());
        emitter.send(
                SseEmitter.event()
                        .name("notify")
                        .data(json, MediaType.APPLICATION_JSON));
        log.debug("[social-sse] notify event written phase={} bytes={}", phase, json.length());
    }

    private int connectionCount(int userId) {
        Set<SseEmitter> set = emittersByUser.get(userId);
        return set != null ? set.size() : 0;
    }

    private String onlineUserIdsSnapshot() {
        return emittersByUser.keySet().toString();
    }

    private void sendHeartbeatsToAll() {
        for (Map.Entry<Integer, Set<SseEmitter>> e : emittersByUser.entrySet()) {
            int userId = e.getKey();
            Set<SseEmitter> set = e.getValue();
            if (set == null || set.isEmpty()) {
                continue;
            }
            for (SseEmitter emitter : set) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException ex) {
                    unregister(userId, emitter, "heartbeat-failed");
                    try {
                        emitter.completeWithError(ex);
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdownNow();
        for (Set<SseEmitter> set : emittersByUser.values()) {
            for (SseEmitter emitter : set) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        emittersByUser.clear();
    }
}
