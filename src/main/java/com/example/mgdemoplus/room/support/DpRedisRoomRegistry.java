package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Redis-backed authoritative room state for multi-instance P0.
 */
@Component
public class DpRedisRoomRegistry implements DpRoomRegistry {

    private static final Logger log = LoggerFactory.getLogger(DpRedisRoomRegistry.class);
    private static final int LOCK_RETRY_MS = 25;
    private static final int LOCK_MAX_WAIT_MS = 5_000;

    private final StringRedisTemplate stringRedisTemplate;
    private final DpRoomRedisCodec codec;
    private final DpRoomDistributedLock distributedLock;
    private final DpRoomEventPublisher eventPublisher;

    public DpRedisRoomRegistry(
            StringRedisTemplate stringRedisTemplate,
            DpRoomRedisCodec codec,
            DpRoomDistributedLock distributedLock,
            DpRoomEventPublisher eventPublisher) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.codec = codec;
        this.distributedLock = distributedLock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Map<String, DpRoomBO> roomMap() {
        Map<String, DpRoomBO> view = new HashMap<>();
        for (String roomId : roomIds()) {
            DpRoomBO r = get(roomId);
            if (r != null) {
                view.put(roomId, r);
            }
        }
        return Collections.unmodifiableMap(view);
    }

    @Override
    public DpRoomBO get(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(DpRoomRedisKeys.stateKey(roomId));
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return codec.fromJson(json);
        } catch (Exception e) {
            log.warn("deserialize room failed roomId={}", roomId, e);
            return null;
        }
    }

    @Override
    public void put(String roomId, DpRoomBO room) {
        saveAfterMutation(roomId, room, "put");
    }

    @Override
    public Collection<DpRoomBO> values() {
        Set<String> ids = roomIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        Collection<DpRoomBO> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            DpRoomBO r = get(id);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    @Override
    public Set<String> roomIds() {
        Set<String> members = stringRedisTemplate.opsForSet().members(DpRoomRedisKeys.INDEX);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream().filter(id -> id != null && !id.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean contains(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(DpRoomRedisKeys.stateKey(roomId)));
    }

    @Override
    public boolean tryUnregisterEmptyRoomAssumeLocked(
            DpRoomBO r, String roomId, int liveHumanTableCount, int spectatorCount) {
        if (r == null || roomId == null || roomId.isEmpty()) {
            return false;
        }
        if (liveHumanTableCount > 0 || spectatorCount > 0) {
            return false;
        }
        if (!contains(roomId)) {
            return false;
        }
        stringRedisTemplate.delete(DpRoomRedisKeys.stateKey(roomId));
        stringRedisTemplate.delete(DpRoomRedisKeys.revKey(roomId));
        stringRedisTemplate.opsForSet().remove(DpRoomRedisKeys.INDEX, roomId);
        eventPublisher.publish(roomId, 0L, "roomRemoved");
        return true;
    }

    @Override
    public <T> T runExclusive(String roomId, Function<DpRoomBO, T> action) {
        String token = acquireWithRetry(roomId);
        if (token == null) {
            throw new IllegalStateException("room lock timeout roomId=" + roomId);
        }
        try {
            DpRoomBO r = get(roomId);
            if (r == null) {
                return null;
            }
            T result = action.apply(r);
            if (contains(roomId)) {
                saveAfterMutation(roomId, r, "mutation");
            }
            return result;
        } finally {
            distributedLock.release(roomId, token);
        }
    }

    @Override
    public boolean isRedisBacked() {
        return true;
    }

    @Override
    public void saveAfterMutation(String roomId, DpRoomBO room, String reason) {
        if (roomId == null || roomId.isEmpty() || room == null) {
            return;
        }
        try {
            room.setStorageVersion(room.getStorageVersion() + 1L);
            String json = codec.toJson(room);
            stringRedisTemplate.opsForValue().set(DpRoomRedisKeys.stateKey(roomId), json);
            stringRedisTemplate.opsForSet().add(DpRoomRedisKeys.INDEX, roomId);
            Long rev = stringRedisTemplate.opsForValue().increment(DpRoomRedisKeys.revKey(roomId));
            long revision = rev != null ? rev : 0L;
            eventPublisher.publish(roomId, revision, reason != null ? reason : "mutation");
        } catch (Exception e) {
            throw new IllegalStateException("save room to redis failed roomId=" + roomId, e);
        }
    }

    @Override
    public long revision(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return 0L;
        }
        String rev = stringRedisTemplate.opsForValue().get(DpRoomRedisKeys.revKey(roomId));
        if (rev == null || rev.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(rev);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String acquireWithRetry(String roomId) {
        long deadline = System.currentTimeMillis() + LOCK_MAX_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            String token = distributedLock.tryAcquire(roomId);
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
}
