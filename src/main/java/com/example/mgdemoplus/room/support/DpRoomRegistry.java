package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@code roomId → DpRoomBO} registry for the single-node room runtime.
 */
public final class DpRoomRegistry {

    private final Map<String, DpRoomBO> roomMap = new ConcurrentHashMap<>();

    public Map<String, DpRoomBO> roomMap() {
        return roomMap;
    }

    public DpRoomBO get(String roomId) {
        return roomMap.get(roomId);
    }

    public void put(String roomId, DpRoomBO room) {
        roomMap.put(roomId, room);
    }

    public Collection<DpRoomBO> values() {
        return roomMap.values();
    }

    public Set<String> roomIds() {
        return Set.copyOf(roomMap.keySet());
    }

    public boolean contains(String roomId) {
        return roomMap.containsKey(roomId);
    }

    /**
     * 空房且无观众时摘除条目。必须在已持有 {@code synchronized(r)} 下调用。
     */
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
}
