package com.example.mgdemoplus.dp.quickmatch.pairing;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.dp.quickmatch.JoinableQuickMatchRoomIndex;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 占位编译 + 烟雾：空队列下 {@link DpQuickMatchPairingCoordinator#attemptPairing()} 应迅速返回。
 * Agent C 接入真实 Host 后应补充业务向测试。
 */
class DpQuickMatchPairingCoordinatorTest {

    @Test
    void attemptPairing_emptyQueue_exits() {
        StubHost host = new StubHost();
        DpQuickMatchPairingCoordinator coordinator = new DpQuickMatchPairingCoordinator(host);
        assertDoesNotThrow(coordinator::attemptPairing);
    }

    private static final class StubHost implements DpQuickMatchPairingHost {

        private final Object qLock = new Object();
        private final Map<String, DpRoomBO> roomMap = new ConcurrentHashMap<>();
        private final JoinableQuickMatchRoomIndex index = new JoinableQuickMatchRoomIndex();
        private final ArrayDeque<DpQuickMatchWaitEntry> waiters = new ArrayDeque<>();

        @Override
        public Object defaultQmLock() {
            return qLock;
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
            // stub
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
