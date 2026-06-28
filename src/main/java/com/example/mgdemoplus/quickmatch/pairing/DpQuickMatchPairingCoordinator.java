package com.example.mgdemoplus.quickmatch.pairing;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.quickmatch.DpQuickMatchRoomSemantics;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 新快匹「尝试配对」单入口协调器：先尽量把队列流入「房索引有桌」的公开房；否则在无桌且队列至少两人时，
 * 自队头批量取出至多 {@value #MAX_NEW_ROOM_BATCH} 人建房开局。
 */
public final class DpQuickMatchPairingCoordinator {

    /** 无桌时单次出队上限，与默认快匹最大座位一致。 */
    public static final int MAX_NEW_ROOM_BATCH = DpRoomBO.MAX_SEAT_COUNT;

    private final DpQuickMatchPairingHost host;

    private final AtomicInteger pairingDepth = new AtomicInteger();

    public DpQuickMatchPairingCoordinator(DpQuickMatchPairingHost host) {
        this.host = host;
    }

    /**
     * 单入口：循环尝试填充已有桌或批量建房，直到本轮无进展。
     */
    public void attemptPairing() {
        int d = pairingDepth.incrementAndGet();
        try {
            if (d > 1) {
                host.flushQueuedWaitersIntoJoinablePublicRooms();
                return;
            }
            progressLoop:
            while (true) {
                host.flushQueuedWaitersIntoJoinablePublicRooms();

                JoinableQuickMatchRoomIndex index = host.joinableQuickMatchRoomIndex();
                var vacancyBucket = index.firstVacancyBucket();
                if (vacancyBucket.isPresent()) {
                    List<String> ids = new ArrayList<>(index.roomIdsInBucket(vacancyBucket.get()));
                    boolean filledAny = false;
                    for (String roomId : ids) {
                        if (fillHeadIntoJoinableRoom(roomId)) {
                            filledAny = true;
                            break;
                        }
                    }
                    if (filledAny) {
                        continue progressLoop;
                    }
                }

                if (!drainBatchNewRoomWhenNoJoinableTable()) {
                    break;
                }
            }
        } finally {
            pairingDepth.decrementAndGet();
        }
    }

    private boolean fillHeadIntoJoinableRoom(String roomId) {
        JoinableQuickMatchRoomIndex index = host.joinableQuickMatchRoomIndex();
        if (host.getRoom(roomId) == null) {
            index.remove(roomId);
            return true;
        }

        AtomicReference<List<DpQuickMatchWaitEntry>> batchRef = new AtomicReference<>(List.of());
        AtomicReference<List<DpQuickMatchWaitEntry>> failedRef = new AtomicReference<>(List.of());

        Boolean progressed = host.runExclusiveRoom(roomId, r -> {
            if (r == null || !roomId.equals(r.getRoomId())) {
                return false;
            }
            long now = host.nowMillis();
            if (r.isPasswordProtected()) {
                return false;
            }
            int vac = DpQuickMatchRoomSemantics.vacancyForQuickMatch(r, now);
            if (vac <= 0) {
                return false;
            }
            host.pruneQueueWhileLocked();
            int qSize = host.defaultQueueSizeWhileLocked();
            int k = Math.min(vac, qSize);
            if (k <= 0) {
                return false;
            }
            List<DpQuickMatchWaitEntry> batch = host.pollHeadWhileLocked(k);
            batchRef.set(batch);
            if (batch.isEmpty()) {
                return false;
            }
            List<DpQuickMatchWaitEntry> failedRequeue = new ArrayList<>();
            for (DpQuickMatchWaitEntry e : batch) {
                if (host.nicknameAlreadyInSomeRoom(e.nickname())) {
                    continue;
                }
                try {
                    host.joinAndReadyWhileRoomLocked(r, e);
                    host.notifyQuickMatchMatched(e.nickname(), roomId);
                } catch (RuntimeException ex) {
                    failedRequeue.add(e);
                }
            }
            failedRef.set(failedRequeue);
            return true;
        });

        List<DpQuickMatchWaitEntry> failedRequeue = failedRef.get();
        if (!failedRequeue.isEmpty()) {
            host.unshiftHeadWhileLocked(failedRequeue);
        }

        List<DpQuickMatchWaitEntry> batch = batchRef.get();
        if (!Boolean.TRUE.equals(progressed) || batch.isEmpty()) {
            return false;
        }
        host.afterRoomMutationRefreshQuickMatchIndex(roomId, "ok");
        return true;
    }

    private boolean drainBatchNewRoomWhenNoJoinableTable() {
        host.pruneQueueWhileLocked();
        int sz = host.defaultQueueSizeWhileLocked();
        if (sz <= 1) {
            return false;
        }
        int n = Math.min(MAX_NEW_ROOM_BATCH, sz);
        List<DpQuickMatchWaitEntry> batch = host.pollHeadWhileLocked(n);
        if (batch.size() < 2) {
            host.unshiftHeadWhileLocked(batch);
            return false;
        }

        if (batch.get(0).nickname().equals(batch.get(1).nickname())) {
            host.addTailWhileLocked(batch);
            return false;
        }

        DpQuickMatchWaitEntry owner = batch.get(0);
        DpRoomBO room = host.createDefaultQuickMatchRoomForPairing(owner);
        String newRoomId = room.getRoomId();

        host.runExclusiveRoom(newRoomId, r -> {
            for (int i = 1; i < batch.size(); i++) {
                DpQuickMatchWaitEntry e = batch.get(i);
                if (host.nicknameAlreadyInSomeRoom(e.nickname())) {
                    continue;
                }
                host.joinAndReadyWhileRoomLocked(r, e);
                host.notifyQuickMatchMatched(e.nickname(), newRoomId);
            }
            return null;
        });

        host.afterRoomMutationRefreshQuickMatchIndex(newRoomId, "ok");
        host.notifyQuickMatchMatched(owner.nickname(), newRoomId);
        host.startGameForQuickMatchRoom(newRoomId, owner.nickname());

        host.flushQueuedWaitersIntoJoinablePublicRooms();
        return true;
    }
}
