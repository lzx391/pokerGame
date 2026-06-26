package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.config.DpInstanceProperties;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory {@link DpRoomRegistry} for unit tests only (no Spring bean).
 */
final class InMemoryRoomRegistryForTests implements DpRoomRegistry {

    private final Map<String, DpRoomBO> roomMap = new ConcurrentHashMap<>();

    @Override
    public Map<String, DpRoomBO> roomMap() {
        return roomMap;
    }

    @Override
    public DpRoomBO get(String roomId) {
        return roomMap.get(roomId);
    }

    @Override
    public void put(String roomId, DpRoomBO room) {
        roomMap.put(roomId, room);
    }

    @Override
    public Collection<DpRoomBO> values() {
        return roomMap.values();
    }

    @Override
    public Set<String> roomIds() {
        return Set.copyOf(roomMap.keySet());
    }

    @Override
    public boolean contains(String roomId) {
        return roomMap.containsKey(roomId);
    }

    @Override
    public boolean tryUnregisterEmptyRoomAssumeLocked(
            DpRoomBO r, String roomId, int liveHumanTableCount, int spectatorCount) {
        if (r == null || roomId == null || roomId.isEmpty()) {
            return false;
        }
        if (roomMap.get(roomId) != r) {
            return false;
        }
        if (liveHumanTableCount > 0 || spectatorCount > 0) {
            return false;
        }
        roomMap.remove(roomId);
        return true;
    }

    @Override
    public <T> T runExclusive(String roomId, Function<DpRoomBO, T> action) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return null;
        }
        synchronized (r) {
            return action.apply(r);
        }
    }

    @Override
    public boolean isRedisBacked() {
        return false;
    }

    @Override
    public void saveAfterMutation(String roomId, DpRoomBO room, String reason) {
        // in-memory: mutations apply to live object already in map
    }

    @Override
    public long revision(String roomId) {
        return 0L;
    }
}

/**
 * Test / manual wiring helper when Spring context is not used.
 */
public final class DpRoomTestSupport {

    private DpRoomTestSupport() {
    }

    public static InMemoryRoomRegistryForTests memoryRegistry() {
        return new InMemoryRoomRegistryForTests();
    }

    public static DpInstanceProperties defaultInstanceProperties() {
        DpInstanceProperties p = new DpInstanceProperties();
        p.setInstanceId("test");
        return p;
    }

    /** Resolve the registry wired into a manually constructed {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl}. */
    public static DpRoomRegistry registryFrom(com.example.mgdemoplus.room.impl.DpRoomServiceImpl svc)
            throws Exception {
        java.lang.reflect.Field f = com.example.mgdemoplus.room.impl.DpRoomServiceImpl.class
                .getDeclaredField("registry");
        f.setAccessible(true);
        return (DpRoomRegistry) f.get(svc);
    }

    public static void putRoom(com.example.mgdemoplus.room.impl.DpRoomServiceImpl svc, String roomId, DpRoomBO room)
            throws Exception {
        registryFrom(svc).put(roomId, room);
    }

    public static DpRoomBO getRoom(com.example.mgdemoplus.room.impl.DpRoomServiceImpl svc, String roomId)
            throws Exception {
        return registryFrom(svc).get(roomId);
    }
}
