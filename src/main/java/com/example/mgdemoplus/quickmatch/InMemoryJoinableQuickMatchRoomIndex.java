package com.example.mgdemoplus.quickmatch;

import com.example.mgdemoplus.common.bo.DpRoomBO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * In-memory joinable-room index for unit tests (no Redis). Production uses
 * {@link JoinableQuickMatchRoomIndex} with {@link org.springframework.data.redis.core.StringRedisTemplate}.
 */
final class InMemoryJoinableQuickMatchRoomIndex {

    private final Object indexLock = new Object();
    private final TreeMap<Integer, NavigableSet<String>> byShortage = new TreeMap<>();
    private final Map<String, Integer> roomIdToShortage = new HashMap<>();

    void addOrRefresh(String roomId, DpRoomBO room, long nowMs) {
        if (roomId == null) {
            return;
        }
        synchronized (indexLock) {
            removeUnderLock(roomId);
            if (!DpQuickMatchRoomSemantics.shouldIndexPublicQuickMatchRoom(room, nowMs)) {
                return;
            }
            int key = DpQuickMatchRoomSemantics.vacancyBucketKeyForIndex(room, nowMs);
            if (key <= 0) {
                return;
            }
            byShortage.computeIfAbsent(key, k -> new TreeSet<>()).add(roomId);
            roomIdToShortage.put(roomId, key);
        }
    }

    void remove(String roomId) {
        if (roomId == null) {
            return;
        }
        synchronized (indexLock) {
            removeUnderLock(roomId);
        }
    }

    Optional<String> pollBestCandidate(Function<String, DpRoomBO> roomById, Predicate<DpRoomBO> rule) {
        if (roomById == null || rule == null) {
            return Optional.empty();
        }
        synchronized (indexLock) {
            Iterator<Map.Entry<Integer, NavigableSet<String>>> it = byShortage.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, NavigableSet<String>> e = it.next();
                NavigableSet<String> set = e.getValue();
                if (set == null || set.isEmpty()) {
                    it.remove();
                    continue;
                }
                List<String> snapshot = new ArrayList<>(set);
                for (String rid : snapshot) {
                    DpRoomBO r = roomById.apply(rid);
                    if (r == null) {
                        removeUnderLock(rid);
                        continue;
                    }
                    if (rule.test(r)) {
                        removeUnderLock(rid);
                        return Optional.of(rid);
                    }
                }
            }
            return Optional.empty();
        }
    }

    Optional<Integer> firstVacancyBucket() {
        synchronized (indexLock) {
            if (byShortage.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(byShortage.firstKey());
        }
    }

    List<String> roomIdsInBucket(int shortage) {
        synchronized (indexLock) {
            NavigableSet<String> set = byShortage.get(shortage);
            if (set == null || set.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(set);
        }
    }

    int indexedRoomCount() {
        synchronized (indexLock) {
            return roomIdToShortage.size();
        }
    }

    void rebuildAll(Map<String, DpRoomBO> roomMap, long nowMs) {
        synchronized (indexLock) {
            byShortage.clear();
            roomIdToShortage.clear();
            if (roomMap == null || roomMap.isEmpty()) {
                return;
            }
            for (Map.Entry<String, DpRoomBO> e : roomMap.entrySet()) {
                addOrRefreshUnderLock(e.getKey(), e.getValue(), nowMs);
            }
        }
    }

    private void addOrRefreshUnderLock(String roomId, DpRoomBO room, long nowMs) {
        removeUnderLock(roomId);
        if (!DpQuickMatchRoomSemantics.shouldIndexPublicQuickMatchRoom(room, nowMs)) {
            return;
        }
        int key = DpQuickMatchRoomSemantics.vacancyBucketKeyForIndex(room, nowMs);
        if (key <= 0) {
            return;
        }
        byShortage.computeIfAbsent(key, k -> new TreeSet<>()).add(roomId);
        roomIdToShortage.put(roomId, key);
    }

    private void removeUnderLock(String roomId) {
        Integer k = roomIdToShortage.remove(roomId);
        if (k == null) {
            return;
        }
        NavigableSet<String> set = byShortage.get(k);
        if (set == null) {
            return;
        }
        set.remove(roomId);
        if (set.isEmpty()) {
            byShortage.remove(k);
        }
    }
}
