package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.quickmatch.DpQuickMatchPairingLock;
import com.example.mgdemoplus.quickmatch.DpQuickMatchRoomSemantics;
import com.example.mgdemoplus.quickmatch.DpQuickMatchWaitQueue;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.quickmatch.notify.QuickMatchEventPublisher;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchPairingCoordinator;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry;
import com.example.mgdemoplus.utils.ResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis-backed quick-match FIFO queue, join-and-ready scan, and pairing coordinator wiring.
 */
public final class DpRoomQuickMatchBridge {

    private static final Logger log = LoggerFactory.getLogger(DpRoomQuickMatchBridge.class);

    public static final int DEFAULT_QM_SB = 5;
    public static final int DEFAULT_QM_BB = 10;
    public static final int DEFAULT_QM_STARTING_BB = 50;
    public static final int DEFAULT_QM_MAX_SEATS = DpRoomBO.MAX_SEAT_COUNT;
    private static final long DEFAULT_QM_WAIT_MS = 3L * 60L * 1000L;

    /** 与 {@link #quickMatchJoinAndReady} 中「无候选公开房」文案保持一致。 */
    public static final String MSG_NO_PUBLIC_ROOM = "暂无可匹配的公开房间";

    private final DpRoomRegistry registry;
    private final DpRoomLobbySync lobbySync;
    private final QuickMatchEventPublisher quickMatchEvents;
    private final DpRoomServiceCallbacks callbacks;
    private final DpQuickMatchWaitQueue waitQueue;
    private final DpQuickMatchPairingLock pairingLock;
    private final DpQuickMatchPairingCoordinator qmPairingCoordinator;

    private static final ThreadLocal<Integer> PAIRING_LOCK_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<String> PAIRING_LOCK_TOKEN = new ThreadLocal<>();

    public DpRoomQuickMatchBridge(
            DpRoomRegistry registry,
            DpRoomLobbySync lobbySync,
            QuickMatchEventPublisher quickMatchEvents,
            DpQuickMatchWaitQueue waitQueue,
            DpQuickMatchPairingLock pairingLock,
            DpRoomServiceCallbacks callbacks) {
        this.registry = registry;
        this.lobbySync = lobbySync;
        this.quickMatchEvents = quickMatchEvents;
        this.waitQueue = waitQueue;
        this.pairingLock = pairingLock;
        this.callbacks = callbacks;
        this.qmPairingCoordinator = new DpQuickMatchPairingCoordinator(new DpRoomQuickMatchPairingHost(this));
    }

    public DpRoomRegistry registry() {
        return registry;
    }

    public JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex() {
        return lobbySync.joinableQuickMatchRoomIndex();
    }

    public DpRoomServiceCallbacks callbacks() {
        return callbacks;
    }

    public QuickMatchEventPublisher quickMatchEvents() {
        return quickMatchEvents;
    }

    public DpQuickMatchWaitQueue waitQueue() {
        return waitQueue;
    }

    public DpQuickMatchPairingLock pairingLock() {
        return pairingLock;
    }

    /** Best-effort pairing (e.g. after REST enqueue); skips if global lock is held elsewhere. */
    public void attemptQuickMatchPairing() {
        if (!enterPairingLockTry()) {
            return;
        }
        try {
            qmPairingCoordinator.attemptPairing();
        } finally {
            exitPairingLock();
        }
    }

    /** Room create/exit: wait briefly for global lock so queued waiters are not left behind. */
    public void attemptQuickMatchPairingBlocking() {
        if (!enterPairingLockBlocking()) {
            return;
        }
        try {
            qmPairingCoordinator.attemptPairing();
        } finally {
            exitPairingLock();
        }
    }

    public void cancelDefaultQuickMatchWaitForOwner(String ownerNickname) {
        if (ownerNickname == null || ownerNickname.isBlank()) {
            return;
        }
        if (!enterPairingLockTry()) {
            return;
        }
        try {
            waitQueue.removeByNicknameWhileLocked(ownerNickname);
        } finally {
            exitPairingLock();
        }
    }

    public ResultUtil quickMatchJoinAndReady(String nickname, Integer userId) {
        if (nickname == null || nickname.isBlank()) {
            return ResultUtil.error().data("message", "昵称无效");
        }
        if (!enterPairingLockBlocking()) {
            return ResultUtil.error().data("message", "匹配服务繁忙，请稍后重试");
        }
        try {
            return quickMatchJoinAndReadyWhilePairingLocked(nickname, userId);
        } finally {
            exitPairingLock();
        }
    }

    private ResultUtil quickMatchJoinAndReadyWhilePairingLocked(String nickname, Integer userId) {
            DpRoomBO existing = callbacks.findRoomContainingNickname(nickname);
            if (existing != null) {
                String err = registry.runExclusive(existing.getRoomId(), r -> {
                    if (r == null) {
                        return "房间不存在";
                    }
                    return finishQuickMatchForExistingPresence(r, nickname, userId);
                });
                if (err != null) {
                    return ResultUtil.error().data("message", err);
                }
                DpRoomBO live = registry.get(existing.getRoomId());
                if (live != null) {
                    callbacks.presenceMarkInGameHuman(live, nickname, userId, "quick_match_already_in_room");
                }
                lobbySync.refreshJoinableQmIndexThenSyncLobby(existing.getRoomId());
                return ResultUtil.ok()
                        .data("roomId", existing.getRoomId())
                        .data("message", "你已在该房间，已更新准备状态");
            }

            long sortTime = System.currentTimeMillis();
            List<DpRoomBO> candidates = orderedQuickMatchJoinCandidates(sortTime);

            for (DpRoomBO candidate : candidates) {
                String roomId = candidate.getRoomId();
                JoinOutcome outcome = registry.runExclusive(roomId, r -> tryJoinCandidateRoom(r, nickname, userId, sortTime));
                if (outcome == null || !outcome.progressed()) {
                    continue;
                }
                if (!outcome.readyOk()) {
                    if (!outcome.wasPresent()) {
                        callbacks.exitRoom(roomId, nickname);
                    }
                    continue;
                }
                callbacks.refreshQmIndexAfterJoinOutcomeOutsideRoomLock(roomId, outcome.joinMsg());
                return ResultUtil.ok().data("roomId", roomId).data("message", "ok");
            }
            return ResultUtil.error().data("message", MSG_NO_PUBLIC_ROOM);
    }

    public void pushQuickMatchLobbySnapshot(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        if (!enterPairingLockTry()) {
            return;
        }
        try {
            List<String> qmTimedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
            int queuePos = waitQueue.queuePositionWhileLocked(nickname);
            notifyQuickMatchTimedOut(qmTimedOut);
            if (queuePos > 0) {
                quickMatchEvents.publishWaiting(nickname, queuePos);
                return;
            }
        } finally {
            exitPairingLock();
        }
        DpRoomBO inRoom = callbacks.findRoomContainingNickname(nickname);
        if (inRoom != null) {
            quickMatchEvents.publishMatched(nickname, inRoom.getRoomId());
        }
    }

    public ResultUtil quickMatchJoinQueueOrImmediate(String nickname, Integer userId) {
        long now = System.currentTimeMillis();
        if (!enterPairingLockBlocking()) {
            return ResultUtil.error().data("message", "匹配服务繁忙，请稍后重试");
        }
        try {
            List<String> qmTimedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
            waitQueue.enqueueTailIfAbsentWhileLocked(new DpQuickMatchWaitEntry(nickname, userId, now));
            int queuePos = waitQueue.queuePositionWhileLocked(nickname);
            notifyQuickMatchTimedOut(qmTimedOut);
            if (queuePos > 0) {
                quickMatchEvents.publishWaiting(nickname, queuePos);
            }
        } finally {
            exitPairingLock();
        }

        attemptQuickMatchPairing();

        if (enterPairingLockTry()) {
            try {
                int queuePos = waitQueue.queuePositionWhileLocked(nickname);
                if (queuePos > 0) {
                    quickMatchEvents.publishWaiting(nickname, queuePos);
                }
            } finally {
                exitPairingLock();
            }
        }
        return ResultUtil.ok()
                .data("queued", true)
                .data("state", "WAITING")
                .data("message", "已加入匹配队列，凑齐另一名玩家后将自动建房");
    }

    public boolean cancelDefaultQuickMatchWait(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }
        if (!enterPairingLockTry()) {
            return false;
        }
        boolean removed;
        try {
            removed = waitQueue.removeByNicknameWhileLocked(nickname);
        } finally {
            exitPairingLock();
        }
        if (removed) {
            quickMatchEvents.publishIdle(nickname, "已取消匹配");
        }
        return removed;
    }

    public List<String> scheduledPruneDefaultQuickMatchQueue() {
        if (!enterPairingLockTry()) {
            return List.of();
        }
        List<String> timedOut;
        try {
            timedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
        } finally {
            exitPairingLock();
        }
        notifyQuickMatchTimedOut(timedOut);
        return timedOut;
    }

    List<String> pruneDefaultQuickMatchQueueLockedWithoutReentry() {
        return waitQueue.pruneWhileLocked(
                System.currentTimeMillis(),
                DEFAULT_QM_WAIT_MS,
                nick -> callbacks.findRoomContainingNickname(nick) != null);
    }

    void notifyQuickMatchTimedOut(List<String> nicknames) {
        if (nicknames == null || nicknames.isEmpty()) {
            return;
        }
        for (String n : nicknames) {
            quickMatchEvents.publishIdle(n, "匹配等待超时");
        }
    }

    int defaultQueueSizeWhileLocked() {
        return waitQueue.sizeWhileLocked();
    }

    List<DpQuickMatchWaitEntry> pollHeadWhileLocked(int maxTake) {
        return waitQueue.pollHeadWhileLocked(maxTake);
    }

    void unshiftHeadWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        waitQueue.unshiftHeadWhileLocked(entries);
    }

    void addTailWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        waitQueue.addTailWhileLocked(entries);
    }

    List<DpQuickMatchWaitEntry> defaultQmWaitersSnapshotWhileLocked() {
        return waitQueue.snapshotWhileLocked();
    }

    void removeWaiterWhileLocked(String nickname) {
        waitQueue.removeByNicknameWhileLocked(nickname);
    }

    static String quickMatchResultRoomId(ResultUtil res) {
        if (res == null) {
            return null;
        }
        Map<String, Object> d = res.getData();
        if (d == null || d.get("roomId") == null) {
            return null;
        }
        return String.valueOf(d.get("roomId"));
    }

    private JoinOutcome tryJoinCandidateRoom(DpRoomBO r, String nickname, Integer userId, long sortTimeMs) {
        if (r == null) {
            return JoinOutcome.none();
        }
        long now = System.currentTimeMillis();
        if (r.isPasswordProtected() || DpQuickMatchRoomSemantics.rawVacancyForQuickMatch(r, now) <= 0) {
            return JoinOutcome.none();
        }
        boolean wasPresent = nicknamePresentInRoom(r, nickname);
        String join = callbacks.joinRoomMutateAssumeLocked(r.getRoomId(), nickname, userId, null, r);
        if (!"ok".equals(join) && !"游戏已开始".equals(join)) {
            return JoinOutcome.none();
        }
        boolean readyOk;
        if (r.isPlaying()) {
            readyOk = callbacks.applyReadyNextHandWhileLocked(r, nickname, userId);
        } else {
            readyOk = callbacks.ensureLobbyReady(r, nickname);
        }
        return new JoinOutcome(true, wasPresent, readyOk, join);
    }

    private record JoinOutcome(boolean progressed, boolean wasPresent, boolean readyOk, String joinMsg) {
        static JoinOutcome none() {
            return new JoinOutcome(false, false, false, null);
        }
    }

    private List<DpRoomBO> orderedQuickMatchJoinCandidates(long sortTimeMs) {
        LinkedHashMap<String, DpRoomBO> ordered = new LinkedHashMap<>();
        JoinableQuickMatchRoomIndex index = lobbySync.joinableQuickMatchRoomIndex();
        for (int shortage = 1; shortage <= DpRoomBO.MAX_SEAT_COUNT; shortage++) {
            for (String rid : index.roomIdsInBucket(shortage)) {
                DpRoomBO x = rid == null ? null : registry.get(rid);
                if (x != null && quickMatchEligiblePublicSlice(x, sortTimeMs)) {
                    ordered.putIfAbsent(rid, x);
                }
            }
        }
        List<DpRoomBO> scanned = registry.values().stream()
                .filter(rr -> rr != null && quickMatchEligiblePublicSlice(rr, sortTimeMs))
                .sorted(quickMatchCandComparator(sortTimeMs))
                .toList();
        int fallbackAdds = 0;
        for (DpRoomBO r : scanned) {
            if (!ordered.containsKey(r.getRoomId())) {
                ordered.put(r.getRoomId(), r);
                fallbackAdds++;
            }
        }
        if (fallbackAdds > 0) {
            log.debug(
                    "quickMatch fallback scan added {} candidates not present in JoinableQuickMatchRoomIndex (drift repair)",
                    fallbackAdds);
        }
        return new ArrayList<>(ordered.values());
    }

    private static boolean quickMatchEligiblePublicSlice(DpRoomBO r, long nowMs) {
        return !r.isPasswordProtected() && DpQuickMatchRoomSemantics.rawVacancyForQuickMatch(r, nowMs) > 0;
    }

    private static Comparator<DpRoomBO> quickMatchCandComparator(long sortTimeMs) {
        return (a, b) -> {
            int fa = DpQuickMatchRoomSemantics.quickMatchFillScore(a, sortTimeMs);
            int fb = DpQuickMatchRoomSemantics.quickMatchFillScore(b, sortTimeMs);
            if (fb != fa) {
                return Integer.compare(fb, fa);
            }
            return a.getRoomId().compareTo(b.getRoomId());
        };
    }

    private static boolean isSeatedAlive(DpRoomBO r, String nickname) {
        if (r == null || r.getPlayers() == null || nickname == null) {
            return false;
        }
        for (DpPlayer p : r.getPlayers()) {
            if (p != null && nickname.equals(p.getNickname()) && !p.isLeftThisHand()) {
                return true;
            }
        }
        return false;
    }

    private static boolean nicknamePresentInRoom(DpRoomBO r, String nickname) {
        if (r == null || nickname == null) {
            return false;
        }
        List<String> specs = r.getSpectators();
        if (specs != null && specs.contains(nickname)) {
            return true;
        }
        if (r.getPlayers() != null) {
            for (DpPlayer p : r.getPlayers()) {
                if (p != null && nickname.equals(p.getNickname())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean ensureLobbyReady(DpRoomBO r, String nickname) {
        if (r == null || r.isPlaying() || nickname == null) {
            return false;
        }
        for (DpPlayer p : r.getPlayers()) {
            if (p != null && nickname.equals(p.getNickname())) {
                if (!p.isReady()) {
                    p.setReady(true);
                }
                return true;
            }
        }
        return false;
    }

    private String finishQuickMatchForExistingPresence(DpRoomBO r, String nickname, Integer userId) {
        if (r.isPlaying()) {
            if (isSeatedAlive(r, nickname)) {
                return null;
            }
            return callbacks.applyReadyNextHandWhileLocked(r, nickname, userId)
                    ? null
                    : "候补已满，请稍后再试";
        }
        return ensureLobbyReady(r, nickname) ? null : "准备失败";
    }

    private boolean enterPairingLockTry() {
        int depth = PAIRING_LOCK_DEPTH.get();
        if (depth == 0) {
            String token = pairingLock.tryAcquire();
            if (token == null) {
                return false;
            }
            PAIRING_LOCK_TOKEN.set(token);
        }
        PAIRING_LOCK_DEPTH.set(depth + 1);
        return true;
    }

    private boolean enterPairingLockBlocking() {
        int depth = PAIRING_LOCK_DEPTH.get();
        if (depth == 0) {
            String token = pairingLock.acquireWithRetry();
            if (token == null) {
                return false;
            }
            PAIRING_LOCK_TOKEN.set(token);
        }
        PAIRING_LOCK_DEPTH.set(depth + 1);
        return true;
    }

    private void exitPairingLock() {
        int depth = PAIRING_LOCK_DEPTH.get() - 1;
        if (depth <= 0) {
            PAIRING_LOCK_DEPTH.remove();
            String token = PAIRING_LOCK_TOKEN.get();
            PAIRING_LOCK_TOKEN.remove();
            if (token != null) {
                pairingLock.release(token);
            }
        } else {
            PAIRING_LOCK_DEPTH.set(depth);
        }
    }
}
