package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpPot;
import com.example.mgdemoplus.entity.dp.DpRoom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本桌有玩家时即记录完整牌谱（与是否坐 Shark 无关），供入库与回放。
 * Shark 专用逻辑见 {@link #isSharkAtTable(DpRoom)}（对手记忆等仍仅在桌上有 Shark 时启用）。
 *
 * <p>与 {@link DpNpcSharkHandActionLog} 并行：ActionLog 仍仅在桌上有 Shark 时启用；
 * 本类将结束的整手归档到环形缓冲，不依赖当手临时列表。</p>
 */
final class DpNpcSharkObservedHandHistory {
    private DpNpcSharkObservedHandHistory() {
    }

    /** 每房间最多保留的已完成手数（内存上限） */
    private static final int MAX_COMPLETED_HANDS_PER_ROOM = 400;

    public enum ActionType {
        POST_BLIND_SB,
        POST_BLIND_BB,
        CHECK,
        CALL,
        BET,
        RAISE,
        ALL_IN,
        FOLD
    }

    /** 单条行动（含弃牌、过牌、跟注、加注等） */
    public static final class ActionRecord {
        public final long tsMs;
        public final String stage;
        public final String actorNickname;
        public final ActionType type;
        public final int amount;
        public final int betToCallBefore;
        public final int actorBetBefore;
        public final int raiseLevelAfter;
        public final int potBefore;

        public ActionRecord(long tsMs, String stage, String actorNickname, ActionType type,
                            int amount, int betToCallBefore, int actorBetBefore,
                            int raiseLevelAfter, int potBefore) {
            this.tsMs = tsMs;
            this.stage = stage == null ? "" : stage;
            this.actorNickname = actorNickname == null ? "" : actorNickname;
            this.type = type;
            this.amount = amount;
            this.betToCallBefore = betToCallBefore;
            this.actorBetBefore = actorBetBefore;
            this.raiseLevelAfter = raiseLevelAfter;
            this.potBefore = potBefore;
        }
    }

    /** 某一街的公共牌快照 */
    public static final class StreetBoard {
        public final String stage;
        public final List<String> communityCards;

        public StreetBoard(String stage, List<String> communityCards) {
            this.stage = stage == null ? "" : stage;
            this.communityCards = communityCards == null
                    ? List.of()
                    : List.copyOf(communityCards);
        }
    }

    /** 盲注后、行动开始前每位玩家在桌状态（含座位序） */
    public static final class SeatAtHandStart {
        public final int seatIndex;
        public final String nickname;
        public final int blind; // 0 无 1 SB 2 BB
        public final int chipsAfterBlinds;

        public SeatAtHandStart(int seatIndex, String nickname, int blind, int chipsAfterBlinds) {
            this.seatIndex = seatIndex;
            this.nickname = nickname == null ? "" : nickname;
            this.blind = blind;
            this.chipsAfterBlinds = chipsAfterBlinds;
        }
    }

    /** 结算前某一池（主池/边池） */
    public static final class PotSnapshot {
        public final int amount;
        public final List<String> eligibleNicknames;

        public PotSnapshot(int amount, List<String> eligibleNicknames) {
            this.amount = amount;
            this.eligibleNicknames = eligibleNicknames == null
                    ? List.of()
                    : List.copyOf(eligibleNicknames);
        }
    }

    /**
     * 一手结束后的完整观测记录（服务端视角：含未摊牌者底牌，便于 AI 离线分析；勿直接下发客户端）。
     */
    public static final class ObservedHandRecord {
        public final String roomId;
        public final long handSeed;
        public final long startedAtMs;
        public final long endedAtMs;
        public final int smallBlindChips;
        public final int bigBlindChips;
        public final String dealerNickname;
        public final List<SeatAtHandStart> seatsAtStart;
        public final List<StreetBoard> boardsByStreet;
        public final List<ActionRecord> actions;
        public final List<PotSnapshot> potsBeforeSettlement;
        public final int mainPotTotalBeforeSettlement;
        public final Map<String, List<String>> holeCardsAtEnd;
        public final Map<String, Integer> netChipsChange;

        ObservedHandRecord(String roomId, long handSeed, long startedAtMs, long endedAtMs,
                           int smallBlindChips, int bigBlindChips, String dealerNickname,
                           List<SeatAtHandStart> seatsAtStart, List<StreetBoard> boardsByStreet,
                           List<ActionRecord> actions, List<PotSnapshot> potsBeforeSettlement,
                           int mainPotTotalBeforeSettlement,
                           Map<String, List<String>> holeCardsAtEnd,
                           Map<String, Integer> netChipsChange) {
            this.roomId = roomId == null ? "" : roomId;
            this.handSeed = handSeed;
            this.startedAtMs = startedAtMs;
            this.endedAtMs = endedAtMs;
            this.smallBlindChips = smallBlindChips;
            this.bigBlindChips = bigBlindChips;
            this.dealerNickname = dealerNickname == null ? "" : dealerNickname;
            this.seatsAtStart = List.copyOf(seatsAtStart);
            this.boardsByStreet = List.copyOf(boardsByStreet);
            this.actions = List.copyOf(actions);
            this.potsBeforeSettlement = List.copyOf(potsBeforeSettlement);
            this.mainPotTotalBeforeSettlement = mainPotTotalBeforeSettlement;
            this.holeCardsAtEnd = Map.copyOf(holeCardsAtEnd);
            this.netChipsChange = Map.copyOf(netChipsChange);
        }
    }

    private static final class Key {
        final String roomId;
        final long handSeed;

        Key(String roomId, long handSeed) {
            this.roomId = roomId == null ? "" : roomId;
            this.handSeed = handSeed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return handSeed == key.handSeed && Objects.equals(roomId, key.roomId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, handSeed);
        }
    }

    private static final class HandBuilder {
        long startedAtMs = System.currentTimeMillis();
        String dealerNickname = "";
        final List<SeatAtHandStart> seatsAtStart = new ArrayList<>();
        final List<StreetBoard> boardsByStreet = new ArrayList<>();
        final List<ActionRecord> actions = new ArrayList<>();
        List<PotSnapshot> potsBeforeSettlement = List.of();
        int mainPotTotalBeforeSettlement;
        final Map<String, Integer> chipsAfterBlinds = new HashMap<>();
    }

    private static final ConcurrentHashMap<Key, HandBuilder> BUILDERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ArrayDeque<ObservedHandRecord>> ARCHIVE = new ConcurrentHashMap<>();

    /**
     * 是否为本桌记牌谱：有人上桌即开启（不再要求必须有 Shark）。
     */
    static boolean isEnabledForRoom(DpRoom room) {
        if (room == null || room.getPlayers() == null) {
            return false;
        }
        for (DpPlayer p : room.getPlayers()) {
            if (p != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 桌上是否有 BOT_Shark（仅 {@link DpNpcSharkOpponentMemoryService} 等 Shark 专用逻辑使用）。
     */
    static boolean isSharkAtTable(DpRoom room) {
        if (room == null || room.getPlayers() == null) {
            return false;
        }
        for (DpPlayer p : room.getPlayers()) {
            if (p == null) {
                continue;
            }
            if (DpNpcEngine.SHARK_BOT_NICKNAME.equals(p.getNickname())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取某房间最近若干手完整观测记录（时间顺序：旧 → 新）。
     */
    public static List<ObservedHandRecord> getRecentHands(String roomId, int maxHands) {
        if (roomId == null || maxHands <= 0) {
            return List.of();
        }
        ArrayDeque<ObservedHandRecord> q = ARCHIVE.get(roomId);
        if (q == null || q.isEmpty()) {
            return List.of();
        }
        synchronized (q) {
            int n = Math.min(maxHands, q.size());
            List<ObservedHandRecord> all = new ArrayList<>(q);
            int from = all.size() - n;
            return Collections.unmodifiableList(new ArrayList<>(all.subList(from, all.size())));
        }
    }

    static void beginHand(DpRoom room) {
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = new HandBuilder();
        b.startedAtMs = System.currentTimeMillis();
        BUILDERS.put(k, b);
    }

    /**
     * 发牌与下盲结束后调用：固定座位序、盲注后筹码，并记一条 preflop 空_board。
     */
    static void markHandReadyAfterBlinds(DpRoom room) {
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.get(k);
        if (b == null) return;

        List<DpPlayer> ps = room.getPlayers();
        if (ps != null) {
            for (int i = 0; i < ps.size(); i++) {
                DpPlayer p = ps.get(i);
                if (p == null) continue;
                b.seatsAtStart.add(new SeatAtHandStart(i, p.getNickname(), p.getBlind(), p.getChips()));
                b.chipsAfterBlinds.put(p.getNickname(), p.getChips());
                if (p.isDealer()) {
                    b.dealerNickname = p.getNickname() == null ? "" : p.getNickname();
                }
            }
        }
        b.boardsByStreet.add(new StreetBoard("preflop", room.getCommunityCards() == null
                ? List.of() : new ArrayList<>(room.getCommunityCards())));
    }

    static void recordBoardState(DpRoom room) {
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.get(k);
        if (b == null) return;
        String st = room.getCurrentStage() == null ? "" : room.getCurrentStage();
        if ("showdown".equals(st) || "settled".equals(st)) return;
        List<String> cc = room.getCommunityCards() == null
                ? List.of()
                : new ArrayList<>(room.getCommunityCards());
        b.boardsByStreet.add(new StreetBoard(st, cc));
    }

    static void recordBlind(DpRoom room, String nickname, boolean isSb, int amount, int potBefore) {
        if (!isEnabledForRoom(room)) return;
        ActionType t = isSb ? ActionType.POST_BLIND_SB : ActionType.POST_BLIND_BB;
        append(room, new ActionRecord(System.currentTimeMillis(), "preflop", nickname, t, amount,
                0, 0, room.getRaiseLevel(), potBefore));
    }

    static void recordFold(DpRoom room, DpPlayer actor, int potBefore) {
        if (!isEnabledForRoom(room)) return;
        if (actor == null) return;
        append(room, new ActionRecord(System.currentTimeMillis(), room.getCurrentStage(),
                actor.getNickname(), ActionType.FOLD, 0,
                room.getCurrentBetToCall(), actor.getBet(), room.getRaiseLevel(), potBefore));
    }

    static void recordBetLikeAction(DpRoom room, DpPlayer actor, int amount,
                                    int betToCallBefore, int actorBetBefore, int potBefore,
                                    boolean becameAllIn, boolean isRaise) {
        if (!isEnabledForRoom(room)) return;
        if (actor == null) return;
        ActionType t;
        if (amount <= 0) {
            t = (betToCallBefore <= actorBetBefore) ? ActionType.CHECK : ActionType.CALL;
        } else if (becameAllIn) {
            t = ActionType.ALL_IN;
        } else if (isRaise) {
            t = ActionType.RAISE;
        } else {
            t = (betToCallBefore <= actorBetBefore) ? ActionType.BET : ActionType.CALL;
        }
        append(room, new ActionRecord(System.currentTimeMillis(), room.getCurrentStage(),
                actor.getNickname(), t, amount, betToCallBefore, actorBetBefore,
                room.getRaiseLevel(), potBefore));
    }

    /** 在筹码分配前调用：复制主池与边池结构 */
    static void capturePotsBeforeClear(DpRoom room) {
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.get(k);
        if (b == null) return;
        b.mainPotTotalBeforeSettlement = room.getPot();
        List<DpPot> pots = room.getPots();
        if (pots == null || pots.isEmpty()) {
            b.potsBeforeSettlement = List.of();
            return;
        }
        List<PotSnapshot> snap = new ArrayList<>();
        for (DpPot pot : pots) {
            if (pot == null) continue;
            snap.add(new PotSnapshot(pot.getAmount(),
                    pot.getEligiblePlayers() == null ? List.of() : new ArrayList<>(pot.getEligiblePlayers())));
        }
        b.potsBeforeSettlement = snap;
    }

    /**
     * @return 若本手已归档则返回记录（供入库）；未启用或无构建数据则 null
     */
    static ObservedHandRecord finalizeHand(DpRoom room) {
        if (!isEnabledForRoom(room)) {
            return null;
        }
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.remove(k);
        if (b == null) {
            return null;
        }

        long ended = System.currentTimeMillis();
        Map<String, List<String>> holes = new HashMap<>();
        Map<String, Integer> net = new HashMap<>();
        List<DpPlayer> ps = room.getPlayers();
        if (ps != null) {
            for (DpPlayer p : ps) {
                if (p == null) continue;
                String name = p.getNickname();
                if (name == null) continue;
                List<String> hc = p.getHoleCards();
                if (hc != null && !hc.isEmpty()) {
                    holes.put(name, List.copyOf(hc));
                } else {
                    holes.put(name, List.of());
                }
                int before = b.chipsAfterBlinds.getOrDefault(name, p.getChips());
                net.put(name, p.getChips() - before);
            }
        }

        ObservedHandRecord rec = new ObservedHandRecord(
                room.getRoomId(),
                room.getCurrentHandSeed(),
                b.startedAtMs,
                ended,
                DpRoom.getSBChips(),
                DpRoom.getBBChips(),
                b.dealerNickname,
                b.seatsAtStart,
                b.boardsByStreet,
                b.actions,
                b.potsBeforeSettlement,
                b.mainPotTotalBeforeSettlement,
                holes,
                net
        );

        ARCHIVE.compute(room.getRoomId(), (rid, deque) -> {
            ArrayDeque<ObservedHandRecord> q = deque == null ? new ArrayDeque<>() : deque;
            synchronized (q) {
                q.addLast(rec);
                while (q.size() > MAX_COMPLETED_HANDS_PER_ROOM) {
                    q.removeFirst();
                }
            }
            return q;
        });
        return rec;
    }

    private static void append(DpRoom room, ActionRecord e) {
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.computeIfAbsent(k, kk -> {
            HandBuilder nb = new HandBuilder();
            nb.startedAtMs = System.currentTimeMillis();
            return nb;
        });
        b.actions.add(e);
    }
}
