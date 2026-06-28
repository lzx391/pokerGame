package com.example.mgdemoplus.quickmatch.pairing;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.common.entity.DpPlayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for {@link DpQuickMatchPairingCoordinator}; business paths covered with Redis queue tests.
 */
class DpQuickMatchPairingCoordinatorTest {

    @Test
    void attemptPairing_emptyQueue_exits() {
        StubHost host = new StubHost();
        DpQuickMatchPairingCoordinator coordinator = new DpQuickMatchPairingCoordinator(host);
        assertDoesNotThrow(coordinator::attemptPairing);
    }

    @Test
    void attemptPairing_joinableRoomWithWaiter_pullsHeadIntoRoom() {
        StubHost host = new StubHost();
        long now = System.currentTimeMillis();
        DpRoomBO room = host.newPublicRoom("room-a", "bob", now);
        host.roomMap.put(room.getRoomId(), room);
        host.index.addOrRefresh(room.getRoomId(), room, now);
        host.waiters.addLast(new DpQuickMatchWaitEntry("alice", 1, now));

        new DpQuickMatchPairingCoordinator(host).attemptPairing();

        assertTrue(host.joinedNicknames.contains("alice"), "waiter should join indexed public room");
        assertEquals(0, host.waiters.size(), "waiter should leave queue after pairing");
    }

    private static final class StubHost implements DpQuickMatchPairingHost {

        private final Map<String, DpRoomBO> roomMap = new ConcurrentHashMap<>();
        private final JoinableQuickMatchRoomIndex index = new JoinableQuickMatchRoomIndex();
        private final ArrayDeque<DpQuickMatchWaitEntry> waiters = new ArrayDeque<>();
        private final List<String> joinedNicknames = new ArrayList<>();

        DpRoomBO newPublicRoom(String roomId, String owner, long nowMs) {
            DpRoomBO r = new DpRoomBO();
            r.setRoomId(roomId);
            r.setOwner(owner);
            r.setMaxSeatCount(DpRoomBO.MAX_SEAT_COUNT);
            DpPlayer ownerPlayer = new DpPlayer();
            ownerPlayer.setNickname(owner);
            ownerPlayer.setReady(true);
            ownerPlayer.setLastHeartBeat(nowMs);
            r.getPlayers().add(ownerPlayer);
            return r;
        }

        @Override
        public Map<String, DpRoomBO> roomMap() {
            return roomMap;
        }

        @Override
        public JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex() {
            return index;
        }

        @Override
        public DpRoomBO getRoom(String roomId) {
            return roomMap.get(roomId);
        }

        @Override
        public <T> T runExclusiveRoom(String roomId, Function<DpRoomBO, T> action) {
            DpRoomBO r = roomMap.get(roomId);
            if (r == null) {
                return null;
            }
            synchronized (r) {
                return action.apply(r);
            }
        }

        @Override
        public long nowMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public List<String> pruneQueueWhileLocked() {
            return Collections.emptyList();
        }

        @Override
        public int defaultQueueSizeWhileLocked() {
            return waiters.size();
        }

        @Override
        public List<DpQuickMatchWaitEntry> pollHeadWhileLocked(int maxTake) {
            if (maxTake <= 0 || waiters.isEmpty()) {
                return List.of();
            }
            List<DpQuickMatchWaitEntry> out = new ArrayList<>();
            int n = Math.min(maxTake, waiters.size());
            for (int i = 0; i < n; i++) {
                out.add(waiters.pollFirst());
            }
            return out;
        }

        @Override
        public void unshiftHeadWhileLocked(List<DpQuickMatchWaitEntry> entries) {
            if (entries == null || entries.isEmpty()) {
                return;
            }
            for (int i = entries.size() - 1; i >= 0; i--) {
                waiters.addFirst(entries.get(i));
            }
        }

        @Override
        public void addTailWhileLocked(List<DpQuickMatchWaitEntry> entries) {
            if (entries == null) {
                return;
            }
            waiters.addAll(entries);
        }

        @Override
        public boolean nicknameAlreadyInSomeRoom(String nickname) {
            return false;
        }

        @Override
        public void joinAndReadyWhileRoomLocked(DpRoomBO room, DpQuickMatchWaitEntry entry) {
            joinedNicknames.add(entry.nickname());
            DpPlayer p = new DpPlayer();
            p.setNickname(entry.nickname());
            p.setReady(true);
            p.setDpUserId(entry.userId());
            room.getPlayers().add(p);
        }

        @Override
        public DpRoomBO createDefaultQuickMatchRoomForPairing(DpQuickMatchWaitEntry owner) {
            DpRoomBO r = new DpRoomBO();
            r.setRoomId("stub-" + owner.nickname());
            roomMap.put(r.getRoomId(), r);
            return r;
        }

        @Override
        public void startGameForQuickMatchRoom(String roomId, String ownerNickname) {
            // stub
        }

        @Override
        public void notifyQuickMatchMatched(String nickname, String roomId) {
            // stub
        }

        @Override
        public void afterRoomMutationRefreshQuickMatchIndex(String roomId, String joinDetailMessageOrNull) {
            // stub
        }

        @Override
        public void flushQueuedWaitersIntoJoinablePublicRooms() {
            // stub
        }
    }
}
