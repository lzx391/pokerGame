package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchPairingHost;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry;
import com.example.mgdemoplus.utils.ResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link DpQuickMatchPairingHost} adapter delegating room mutations to {@link DpRoomQuickMatchBridge}.
 */
public final class DpRoomQuickMatchPairingHost implements DpQuickMatchPairingHost {

    private static final Logger log = LoggerFactory.getLogger(DpRoomQuickMatchPairingHost.class);

    private final DpRoomQuickMatchBridge bridge;

    public DpRoomQuickMatchPairingHost(DpRoomQuickMatchBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Object defaultQmLock() {
        return bridge.defaultQmLock();
    }

    @Override
    public Map<String, DpRoomBO> roomMap() {
        return bridge.registry().roomMap();
    }

    @Override
    public com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex() {
        return bridge.joinableQuickMatchRoomIndex();
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public List<String> pruneQueueWhileLocked() {
        List<String> timed = bridge.pruneDefaultQuickMatchQueueLockedWithoutReentry();
        bridge.notifyQuickMatchTimedOut(timed);
        return timed;
    }

    @Override
    public int defaultQueueSizeWhileLocked() {
        return bridge.defaultQueueSizeWhileLocked();
    }

    @Override
    public List<DpQuickMatchWaitEntry> pollHeadWhileLocked(int maxTake) {
        return bridge.pollHeadWhileLocked(maxTake);
    }

    @Override
    public void unshiftHeadWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        bridge.unshiftHeadWhileLocked(entries);
    }

    @Override
    public void addTailWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        bridge.addTailWhileLocked(entries);
    }

    @Override
    public boolean nicknameAlreadyInSomeRoom(String nickname) {
        return bridge.callbacks().findRoomContainingNickname(nickname) != null;
    }

    @Override
    public void joinAndReadyWhileRoomLocked(DpRoomBO room, DpQuickMatchWaitEntry entry) {
        String joinMsg = bridge.callbacks().joinRoomMutateAssumeLocked(
                room.getRoomId(), entry.nickname(), entry.userId(), null, room);
        if (!"ok".equals(joinMsg) && !"游戏已开始".equals(joinMsg)) {
            throw new IllegalStateException("quick match join: " + joinMsg);
        }
        if (room.isPlaying()) {
            if (!bridge.callbacks().applyReadyNextHandWhileLocked(room, entry.nickname(), entry.userId())) {
                throw new IllegalStateException("quick match readyNextHand failed");
            }
        } else {
            if (!bridge.callbacks().ensureLobbyReady(room, entry.nickname())) {
                throw new IllegalStateException("quick match ensureLobbyReady failed");
            }
        }
    }

    @Override
    public DpRoomBO createDefaultQuickMatchRoomForPairing(DpQuickMatchWaitEntry owner) {
        return bridge.callbacks().createRoom(
                owner.nickname(),
                owner.userId(),
                DpRoomQuickMatchBridge.DEFAULT_QM_SB,
                DpRoomQuickMatchBridge.DEFAULT_QM_BB,
                DpRoomQuickMatchBridge.DEFAULT_QM_STARTING_BB,
                null,
                DpRoomQuickMatchBridge.DEFAULT_QM_MAX_SEATS);
    }

    @Override
    public void startGameForQuickMatchRoom(String roomId, String ownerNickname) {
        if (!bridge.callbacks().startGame(roomId, ownerNickname)) {
            log.warn("auto quick-match: startGame failed roomId={} owner={}", roomId, ownerNickname);
        }
    }

    @Override
    public void notifyQuickMatchMatched(String nickname, String roomId) {
        bridge.quickMatchPush().notifyMatched(nickname, roomId);
    }

    @Override
    public void afterRoomMutationRefreshQuickMatchIndex(String roomId, String joinDetailMessageOrNull) {
        bridge.callbacks().refreshQmIndexAfterJoinOutcomeOutsideRoomLock(roomId, joinDetailMessageOrNull);
    }

    @Override
    public void flushQueuedWaitersIntoJoinablePublicRooms() {
        while (true) {
            List<String> qmTimedOut;
            List<DpQuickMatchWaitEntry> snapshot;
            synchronized (bridge.defaultQmLock()) {
                qmTimedOut = bridge.pruneDefaultQuickMatchQueueLockedWithoutReentry();
                snapshot = new ArrayList<>(bridge.defaultQmWaitersSnapshotWhileLocked());
            }
            bridge.notifyQuickMatchTimedOut(qmTimedOut);
            if (snapshot.isEmpty()) {
                return;
            }
            int roundSuccesses = 0;
            for (DpQuickMatchWaitEntry e : snapshot) {
                boolean stillInQueue;
                synchronized (bridge.defaultQmLock()) {
                    stillInQueue = false;
                    for (DpQuickMatchWaitEntry x : bridge.defaultQmWaitersSnapshotWhileLocked()) {
                        if (e.nickname().equals(x.nickname())) {
                            stillInQueue = true;
                            break;
                        }
                    }
                }
                if (!stillInQueue) {
                    continue;
                }
                ResultUtil res = bridge.callbacks().quickMatchJoinAndReady(e.nickname(), e.userId());
                if (!Boolean.TRUE.equals(res.getSuccess())) {
                    continue;
                }
                synchronized (bridge.defaultQmLock()) {
                    bridge.removeWaiterWhileLocked(e.nickname());
                }
                String rid = DpRoomQuickMatchBridge.quickMatchResultRoomId(res);
                if (rid != null) {
                    bridge.quickMatchPush().notifyMatched(e.nickname(), rid);
                }
                roundSuccesses++;
            }
            if (roundSuccesses == 0) {
                return;
            }
        }
    }
}
