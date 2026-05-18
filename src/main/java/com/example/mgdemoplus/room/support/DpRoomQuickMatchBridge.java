package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.quickmatch.DpQuickMatchRoomSemantics;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchPairingCoordinator;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry;
import com.example.mgdemoplus.utils.ResultUtil;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default quick-match FIFO queue, join-and-ready scan, and pairing coordinator wiring.
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
    private final DpQuickMatchPushService quickMatchPush;
    private final DpRoomServiceCallbacks callbacks;

    private final Object defaultQmLock = new Object();
    private final ArrayDeque<DpQuickMatchWaitEntry> defaultQmWaiters = new ArrayDeque<>();
    private final ReentrantLock dpQuickMatchAssignmentLock = new ReentrantLock();
    private final DpQuickMatchPairingCoordinator qmPairingCoordinator;

    public DpRoomQuickMatchBridge(
            DpRoomRegistry registry,
            DpRoomLobbySync lobbySync,
            DpQuickMatchPushService quickMatchPush,
            DpRoomServiceCallbacks callbacks) {
        this.registry = registry;
        this.lobbySync = lobbySync;
        this.quickMatchPush = quickMatchPush;
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

    public DpQuickMatchPushService quickMatchPush() {
        return quickMatchPush;
    }

    public Object defaultQmLock() {
        return defaultQmLock;
    }

    public void attemptQuickMatchPairing() {
        if (!dpQuickMatchAssignmentLock.tryLock()) {
            return;
        }
        try {
            qmPairingCoordinator.attemptPairing();
        } finally {
            dpQuickMatchAssignmentLock.unlock();
        }
    }

    public void cancelDefaultQuickMatchWaitForOwner(String ownerNickname) {
        if (ownerNickname == null || ownerNickname.isBlank()) {
            return;
        }
        synchronized (defaultQmLock) {
            defaultQmWaiters.removeIf(e -> ownerNickname.equals(e.nickname()));
        }
    }

    public ResultUtil quickMatchJoinAndReady(String nickname, Integer userId) {
        if (nickname == null || nickname.isBlank()) {
            return ResultUtil.error().data("message", "昵称无效");
        }
        dpQuickMatchAssignmentLock.lock();
        try {
            DpRoomBO existing = callbacks.findRoomContainingNickname(nickname);
            if (existing != null) {
                String err;
                synchronized (existing) {
                    err = finishQuickMatchForExistingPresence(existing, nickname, userId);
                }
                if (err != null) {
                    return ResultUtil.error().data("message", err);
                }
                callbacks.presenceMarkInGameHuman(existing, nickname, userId, "quick_match_already_in_room");
                lobbySync.refreshJoinableQmIndexThenSyncLobby(existing.getRoomId());
                return ResultUtil.ok()
                        .data("roomId", existing.getRoomId())
                        .data("message", "你已在该房间，已更新准备状态");
            }

            long sortTime = System.currentTimeMillis();
            List<DpRoomBO> candidates = orderedQuickMatchJoinCandidates(sortTime);

            candLoop:
            for (DpRoomBO r : candidates) {
                boolean progressedInRoom = false;
                String join = "";
                boolean wasPresent = false;
                boolean readyOk = true;
                synchronized (r) {
                    if (registry.get(r.getRoomId()) != r) {
                        continue candLoop;
                    }
                    long now = System.currentTimeMillis();
                    if (r.isPasswordProtected()
                            || DpQuickMatchRoomSemantics.rawVacancyForQuickMatch(r, now) <= 0) {
                        continue candLoop;
                    }
                    wasPresent = nicknamePresentInRoom(r, nickname);
                    join = callbacks.joinRoomMutateAssumeLocked(r.getRoomId(), nickname, userId, null, r);
                    if (!"ok".equals(join) && !"游戏已开始".equals(join)) {
                        continue candLoop;
                    }
                    if (r.isPlaying()) {
                        if (!callbacks.applyReadyNextHandWhileLocked(r, nickname, userId)) {
                            readyOk = false;
                        }
                    } else {
                        if (!callbacks.ensureLobbyReady(r, nickname)) {
                            readyOk = false;
                        }
                    }
                    progressedInRoom = true;
                }
                if (!progressedInRoom) {
                    continue candLoop;
                }
                callbacks.refreshQmIndexAfterJoinOutcomeOutsideRoomLock(r.getRoomId(), join);
                if (!readyOk) {
                    if (!wasPresent) {
                        callbacks.exitRoom(r.getRoomId(), nickname);
                    }
                    continue candLoop;
                }
                lobbySync.refreshJoinableQmIndexThenSyncLobby(r.getRoomId());
                return ResultUtil.ok().data("roomId", r.getRoomId()).data("message", "ok");
            }
            return ResultUtil.error().data("message", MSG_NO_PUBLIC_ROOM);
        } finally {
            dpQuickMatchAssignmentLock.unlock();
        }
    }

    public void pushQuickMatchLobbySnapshot(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        List<String> qmTimedOut;
        int queuePos = 0;
        synchronized (defaultQmLock) {
            qmTimedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
            int i = 1;
            for (DpQuickMatchWaitEntry e : defaultQmWaiters) {
                if (nickname.equals(e.nickname())) {
                    queuePos = i;
                    break;
                }
                i++;
            }
        }
        notifyQuickMatchTimedOut(qmTimedOut);
        if (queuePos > 0) {
            quickMatchPush.notifyWaiting(nickname, queuePos);
            return;
        }
        DpRoomBO inRoom = callbacks.findRoomContainingNickname(nickname);
        if (inRoom != null) {
            quickMatchPush.notifyMatched(nickname, inRoom.getRoomId());
        }
    }

    public ResultUtil quickMatchJoinQueueOrImmediate(String nickname, Integer userId) {
        long now = System.currentTimeMillis();
        List<String> qmTimedOut;
        synchronized (defaultQmLock) {
            qmTimedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
            boolean already = false;
            for (DpQuickMatchWaitEntry e : defaultQmWaiters) {
                if (nickname.equals(e.nickname())) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                defaultQmWaiters.addLast(new DpQuickMatchWaitEntry(nickname, userId, now));
            }
        }

        notifyQuickMatchTimedOut(qmTimedOut);
        attemptQuickMatchPairing();

        int queuePos = 0;
        synchronized (defaultQmLock) {
            int i = 1;
            for (DpQuickMatchWaitEntry e : defaultQmWaiters) {
                if (nickname.equals(e.nickname())) {
                    queuePos = i;
                    break;
                }
                i++;
            }
        }
        if (queuePos > 0) {
            quickMatchPush.notifyWaiting(nickname, queuePos);
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
        boolean removed;
        synchronized (defaultQmLock) {
            removed = defaultQmWaiters.removeIf(e -> nickname.equals(e.nickname()));
        }
        if (removed) {
            quickMatchPush.notifyIdle(nickname, "已取消匹配");
        }
        return removed;
    }

    public List<String> scheduledPruneDefaultQuickMatchQueue() {
        List<String> timedOut;
        synchronized (defaultQmLock) {
            timedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
        }
        notifyQuickMatchTimedOut(timedOut);
        return timedOut;
    }

    List<String> pruneDefaultQuickMatchQueueLockedWithoutReentry() {
        long now = System.currentTimeMillis();
        List<String> timedOut = new ArrayList<>();
        defaultQmWaiters.removeIf(e -> {
            boolean tooLong = now - e.enqueuedMs() > DEFAULT_QM_WAIT_MS;
            boolean inRoom = callbacks.findRoomContainingNickname(e.nickname()) != null;
            if (tooLong || inRoom) {
                if (tooLong) {
                    timedOut.add(e.nickname());
                }
                return true;
            }
            return false;
        });
        return timedOut;
    }

    void notifyQuickMatchTimedOut(List<String> nicknames) {
        if (nicknames == null || nicknames.isEmpty()) {
            return;
        }
        for (String n : nicknames) {
            quickMatchPush.notifyIdle(n, "匹配等待超时");
        }
    }

    int defaultQueueSizeWhileLocked() {
        return defaultQmWaiters.size();
    }

    List<DpQuickMatchWaitEntry> pollHeadWhileLocked(int maxTake) {
        if (maxTake <= 0 || defaultQmWaiters.isEmpty()) {
            return List.of();
        }
        int n = Math.min(maxTake, defaultQmWaiters.size());
        List<DpQuickMatchWaitEntry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            DpQuickMatchWaitEntry e = defaultQmWaiters.pollFirst();
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    void unshiftHeadWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            defaultQmWaiters.addFirst(entries.get(i));
        }
    }

    void addTailWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (DpQuickMatchWaitEntry e : entries) {
            defaultQmWaiters.addLast(e);
        }
    }

    List<DpQuickMatchWaitEntry> defaultQmWaitersSnapshotWhileLocked() {
        return new ArrayList<>(defaultQmWaiters);
    }

    void removeWaiterWhileLocked(String nickname) {
        defaultQmWaiters.removeIf(x -> nickname.equals(x.nickname()));
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
}
