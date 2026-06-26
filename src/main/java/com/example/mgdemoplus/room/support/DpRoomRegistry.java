package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Room hot-state store in Redis (multi-instance).
 */
public interface DpRoomRegistry {

    Map<String, DpRoomBO> roomMap();

    DpRoomBO get(String roomId);

    void put(String roomId, DpRoomBO room);

    Collection<DpRoomBO> values();

    Set<String> roomIds();

    boolean contains(String roomId);

    /**
     * 空房且无观众时摘除条目。必须在已持有同房互斥锁下调用。
     */
    boolean tryUnregisterEmptyRoomAssumeLocked(
            DpRoomBO r, String roomId, int liveHumanTableCount, int spectatorCount);

    /**
     * 同房互斥：内存模式 {@code synchronized(room)}；Redis 模式分布式锁 + 写回 + 发布变更。
     */
    <T> T runExclusive(String roomId, Function<DpRoomBO, T> action);

    default void runExclusiveVoid(String roomId, java.util.function.Consumer<DpRoomBO> action) {
        runExclusive(roomId, r -> {
            action.accept(r);
            return null;
        });
    }

    boolean isRedisBacked();

    /** After mutation in Redis mode: persist + bump rev + publish. No-op in memory mode if already same ref. */
    void saveAfterMutation(String roomId, DpRoomBO room, String reason);

    long revision(String roomId);
}
