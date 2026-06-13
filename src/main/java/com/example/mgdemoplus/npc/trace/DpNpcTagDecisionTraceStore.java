package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTraceSummary;
import com.example.mgdemoplus.npc.trace.model.DpNpcHandTraceBundle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 内存 store：begin / append / seal / query / removeRoom；每 room 最多 100 手。 */
public final class DpNpcTagDecisionTraceStore {
    private DpNpcTagDecisionTraceStore() {
    }

    public static volatile boolean ENABLED = true;

    private static final int MAX_HANDS_PER_ROOM = 100;

    private static final class Key {
        final String roomId;
        final long handSeed;

        Key(String roomId, long handSeed) {
            this.roomId = roomId == null ? "" : roomId;
            this.handSeed = handSeed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return handSeed == key.handSeed && Objects.equals(roomId, key.roomId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, handSeed);
        }
    }

    private static final class MutableHand {
        final String roomId;
        final long handSeed;
        final List<DpNpcActionTrace> actions = Collections.synchronizedList(new ArrayList<>());

        MutableHand(String roomId, long handSeed) {
            this.roomId = roomId;
            this.handSeed = handSeed;
        }
    }

    private static final ConcurrentHashMap<Key, MutableHand> MUTABLE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Deque<DpNpcHandTraceBundle>> SEALED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> HAND_INDEX = new ConcurrentHashMap<>();

    public static void beginHand(DpRoomBO room) {
        if (!ENABLED || room == null) {
            return;
        }
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        MUTABLE.put(k, new MutableHand(room.getRoomId(), room.getCurrentHandSeed()));
    }

    static int peekNextActionSeq(DpRoomBO room) {
        if (room == null) {
            return 1;
        }
        MutableHand hand = MUTABLE.get(new Key(room.getRoomId(), room.getCurrentHandSeed()));
        if (hand == null) {
            return 1;
        }
        synchronized (hand.actions) {
            return hand.actions.size() + 1;
        }
    }

    public static void appendAction(DpRoomBO room, DpPlayer bot, DpNpcEngine.BotAction action, DpNpcActionTrace trace) {
        if (!ENABLED || room == null || bot == null || trace == null) {
            return;
        }
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        MutableHand hand = MUTABLE.computeIfAbsent(k, kk -> new MutableHand(room.getRoomId(), room.getCurrentHandSeed()));
        synchronized (hand.actions) {
            hand.actions.add(trace);
        }
    }

    /**
     * 结算路径：将当前 hand 移入 room 环缓冲。无 trace action 时不占槽位，返回 null。
     */
    public static DpNpcHandTraceBundle sealHand(DpRoomBO room) {
        if (!ENABLED || room == null) {
            return null;
        }
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        MutableHand hand = MUTABLE.remove(k);
        if (hand == null) {
            return null;
        }
        List<DpNpcActionTrace> actionsCopy;
        synchronized (hand.actions) {
            if (hand.actions.isEmpty()) {
                return null;
            }
            actionsCopy = new ArrayList<>(hand.actions);
        }
        int handIndex = HAND_INDEX
                .computeIfAbsent(room.getRoomId(), id -> new AtomicInteger(0))
                .incrementAndGet();
        DpNpcHandTraceBundle bundle = new DpNpcHandTraceBundle();
        bundle.roomId = room.getRoomId();
        bundle.handSeed = room.getCurrentHandSeed();
        bundle.inProgress = false;
        bundle.sealedAtMs = System.currentTimeMillis();
        bundle.handIndex = handIndex;
        bundle.dealerNickname = DpNpcTagDecisionTraceSupport.resolveDealerNickname(room.getPlayers());
        bundle.smallBlind = room.getSmallBlindChips();
        bundle.bigBlind = room.getBigBlindChips();
        bundle.actionCount = actionsCopy.size();
        bundle.actions = DpNpcTagDecisionTraceSupport.toSummaries(actionsCopy);
        SEALED.computeIfAbsent(room.getRoomId(), id -> new ArrayDeque<>()).addLast(bundle);
        trimRingBuffer(room.getRoomId());
        storeFullActions(bundle, actionsCopy);
        return bundle;
    }

    private static void storeFullActions(DpNpcHandTraceBundle summaryBundle, List<DpNpcActionTrace> full) {
        FULL_ACTIONS.put(fullKey(summaryBundle.roomId, summaryBundle.handSeed), full);
    }

    private static String fullKey(String roomId, long handSeed) {
        return roomId + ":" + handSeed;
    }

    /** handSeed → 完整 action trace（含 steps/matrix），供 REST detail 查询。 */
    private static final ConcurrentHashMap<String, List<DpNpcActionTrace>> FULL_ACTIONS = new ConcurrentHashMap<>();

    private static void trimRingBuffer(String roomId) {
        Deque<DpNpcHandTraceBundle> deque = SEALED.get(roomId);
        if (deque == null) {
            return;
        }
        synchronized (deque) {
            while (deque.size() > MAX_HANDS_PER_ROOM) {
                DpNpcHandTraceBundle oldest = deque.pollFirst();
                if (oldest != null) {
                    FULL_ACTIONS.remove(fullKey(oldest.roomId, oldest.handSeed));
                }
            }
        }
    }

    public static void clearHand(DpRoomBO room) {
        if (room == null) {
            return;
        }
        MUTABLE.remove(new Key(room.getRoomId(), room.getCurrentHandSeed()));
    }

    public static void removeRoom(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        MUTABLE.keySet().removeIf(k -> roomId.equals(k.roomId));
        Deque<DpNpcHandTraceBundle> deque = SEALED.remove(roomId);
        if (deque != null) {
            synchronized (deque) {
                for (DpNpcHandTraceBundle b : deque) {
                    FULL_ACTIONS.remove(fullKey(b.roomId, b.handSeed));
                }
            }
        }
        HAND_INDEX.remove(roomId);
        FULL_ACTIONS.keySet().removeIf(k -> k.startsWith(roomId + ":"));
    }

    public static List<DpNpcHandTraceBundle> listHandSummaries(String roomId) {
        return listHandSummaries(roomId, null);
    }

    public static List<DpNpcHandTraceBundle> listHandSummaries(String roomId, DpRoomBO liveRoom) {
        List<DpNpcHandTraceBundle> out = new ArrayList<>();
        DpNpcHandTraceBundle inProgress = buildInProgressBundle(roomId, liveRoom);
        if (inProgress != null) {
            out.add(inProgress);
        }
        Deque<DpNpcHandTraceBundle> deque = SEALED.get(roomId);
        if (deque == null || deque.isEmpty()) {
            return out;
        }
        synchronized (deque) {
            List<DpNpcHandTraceBundle> copy = new ArrayList<>(deque);
            for (int i = copy.size() - 1; i >= 0; i--) {
                out.add(copySummaryBundle(copy.get(i)));
            }
        }
        return out;
    }

    public static DpNpcHandTraceBundle getHandBundle(String roomId, long handSeed) {
        return getHandBundle(roomId, handSeed, null);
    }

    public static DpNpcHandTraceBundle getHandBundle(String roomId, long handSeed, DpRoomBO liveRoom) {
        DpNpcHandTraceBundle found = findSealed(roomId, handSeed);
        if (found != null) {
            return copySummaryBundle(found);
        }
        MutableHand mutable = MUTABLE.get(new Key(roomId, handSeed));
        if (mutable == null) {
            return null;
        }
        synchronized (mutable.actions) {
            if (mutable.actions.isEmpty()) {
                return null;
            }
        }
        DpRoomBO roomCtx = liveRoom != null && handSeed == liveRoom.getCurrentHandSeed() ? liveRoom : null;
        return buildMutableBundle(mutable, roomCtx);
    }

    public static DpNpcActionTrace getActionDetail(String roomId, long handSeed, String actionId) {
        DpNpcActionTrace fromSealed = findActionInFullStore(roomId, handSeed, actionId);
        if (fromSealed != null) {
            return fromSealed;
        }
        MutableHand mutable = MUTABLE.get(new Key(roomId, handSeed));
        if (mutable == null || actionId == null) {
            return null;
        }
        synchronized (mutable.actions) {
            for (DpNpcActionTrace a : mutable.actions) {
                if (actionId.equals(a.actionId)) {
                    return copyFullAction(a);
                }
            }
        }
        return null;
    }

    private static DpNpcActionTrace findActionInFullStore(String roomId, long handSeed, String actionId) {
        List<DpNpcActionTrace> full = FULL_ACTIONS.get(fullKey(roomId, handSeed));
        if (full == null || actionId == null) {
            return null;
        }
        synchronized (full) {
            for (DpNpcActionTrace a : full) {
                if (actionId.equals(a.actionId)) {
                    return copyFullAction(a);
                }
            }
        }
        return null;
    }

    private static DpNpcHandTraceBundle buildInProgressBundle(String roomId, DpRoomBO liveRoom) {
        Long preferredSeed = liveRoom != null && roomId.equals(liveRoom.getRoomId())
                ? liveRoom.getCurrentHandSeed()
                : null;
        MutableHand hand = findMutableHand(roomId, preferredSeed);
        if (hand == null) {
            return null;
        }
        DpRoomBO roomCtx = liveRoom != null && hand.handSeed == liveRoom.getCurrentHandSeed() ? liveRoom : null;
        return buildMutableBundle(hand, roomCtx);
    }

    private static MutableHand findMutableHand(String roomId, Long preferredHandSeed) {
        if (preferredHandSeed != null) {
            MutableHand preferred = MUTABLE.get(new Key(roomId, preferredHandSeed));
            if (preferred != null) {
                synchronized (preferred.actions) {
                    if (!preferred.actions.isEmpty()) {
                        return preferred;
                    }
                }
            }
        }
        MutableHand best = null;
        long bestSeed = -1;
        for (Key key : MUTABLE.keySet()) {
            if (!roomId.equals(key.roomId)) {
                continue;
            }
            MutableHand candidate = MUTABLE.get(key);
            if (candidate == null || key.handSeed <= bestSeed) {
                continue;
            }
            synchronized (candidate.actions) {
                if (!candidate.actions.isEmpty()) {
                    best = candidate;
                    bestSeed = key.handSeed;
                }
            }
        }
        return best;
    }

    private static DpNpcHandTraceBundle buildMutableBundle(MutableHand hand, DpRoomBO liveRoom) {
        List<DpNpcActionTrace> actionsCopy;
        synchronized (hand.actions) {
            actionsCopy = new ArrayList<>(hand.actions);
        }
        DpNpcHandTraceBundle bundle = new DpNpcHandTraceBundle();
        bundle.roomId = hand.roomId;
        bundle.handSeed = hand.handSeed;
        bundle.inProgress = true;
        bundle.sealedAtMs = 0;
        bundle.handIndex = HAND_INDEX.computeIfAbsent(hand.roomId, id -> new AtomicInteger(0)).get() + 1;
        if (liveRoom != null) {
            bundle.dealerNickname = DpNpcTagDecisionTraceSupport.resolveDealerNickname(liveRoom.getPlayers());
            bundle.smallBlind = liveRoom.getSmallBlindChips();
            bundle.bigBlind = liveRoom.getBigBlindChips();
        }
        bundle.actionCount = actionsCopy.size();
        bundle.actions = DpNpcTagDecisionTraceSupport.toSummaries(actionsCopy);
        return bundle;
    }

    /** WS push：返回含完整 steps 的 bundle 副本（actions 仍仅 summary 字段）。 */
    public static DpNpcHandTraceBundle bundleForWsPush(DpNpcHandTraceBundle sealed) {
        return sealed != null ? copySummaryBundle(sealed) : null;
    }

    private static DpNpcHandTraceBundle findSealed(String roomId, long handSeed) {
        Deque<DpNpcHandTraceBundle> deque = SEALED.get(roomId);
        if (deque == null) {
            return null;
        }
        synchronized (deque) {
            for (DpNpcHandTraceBundle b : deque) {
                if (b.handSeed == handSeed) {
                    return b;
                }
            }
        }
        return null;
    }

    private static DpNpcHandTraceBundle copySummaryBundle(DpNpcHandTraceBundle src) {
        DpNpcHandTraceBundle copy = new DpNpcHandTraceBundle();
        copy.roomId = src.roomId;
        copy.handSeed = src.handSeed;
        copy.inProgress = src.inProgress;
        copy.sealedAtMs = src.sealedAtMs;
        copy.handIndex = src.handIndex;
        copy.dealerNickname = src.dealerNickname;
        copy.smallBlind = src.smallBlind;
        copy.bigBlind = src.bigBlind;
        copy.actionCount = src.actionCount;
        copy.actions = new ArrayList<>();
        if (src.actions != null) {
            for (DpNpcActionTraceSummary s : src.actions) {
                DpNpcActionTraceSummary c = new DpNpcActionTraceSummary();
                c.actionId = s.actionId;
                c.actionSeq = s.actionSeq;
                c.actorNickname = s.actorNickname;
                c.seatIndex = s.seatIndex;
                c.street = s.street;
                c.timestampMs = s.timestampMs;
                c.finalAction = s.finalAction;
                c.holeCards = s.holeCards != null ? s.holeCards.clone() : new String[0];
                copy.actions.add(c);
            }
        }
        return copy;
    }

    private static DpNpcActionTrace copyFullAction(DpNpcActionTrace src) {
        DpNpcActionTrace copy = new DpNpcActionTrace();
        copy.actionId = src.actionId;
        copy.actionSeq = src.actionSeq;
        copy.actorNickname = src.actorNickname;
        copy.seatIndex = src.seatIndex;
        copy.street = src.street;
        copy.timestampMs = src.timestampMs;
        copy.finalAction = src.finalAction;
        copy.holeCards = src.holeCards != null ? src.holeCards.clone() : new String[0];
        copy.steps = src.steps != null ? new ArrayList<>(src.steps) : new ArrayList<>();
        copy.preflopMatrix = src.preflopMatrix;
        copy.preflopMatrixSecondary = src.preflopMatrixSecondary;
        copy.raiseMeta = src.raiseMeta;
        return copy;
    }
}
