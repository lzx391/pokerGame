package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逐街动作内存日志（全桌共用）：用于玩家统计与学习外的决策辅助。
 *
 * <p>
 * 记录 flop/turn/river 谁在激进、谁在街口弃牌等信息；与是否坐 BOT_Shark 无关。
 * 仅保留当前这一手的内存记录，结算后清空。
 * </p>
 */
final class DpNpcSharkHandActionLog {
    private DpNpcSharkHandActionLog() {
    }

    enum ActionType {
        POST_BLIND_SB,
        POST_BLIND_BB,
        CHECK,
        CALL,
        BET,
        RAISE,
        ALL_IN,
        FOLD
    }

    static final class ActionEvent {
        final long ts;
        final String stage; // preflop/flop/turn/river
        final String actor; // nickname
        final ActionType type;
        final int amount; // 本次投入金额（check/fold 为 0）
        final int betToCallBefore;
        final int actorBetBefore;
        final int raiseLevelAfter;
        final int potBefore; // 发生该动作前的底池（用于按尺度分桶：bet/pot）

        ActionEvent(long ts,
                String stage,
                String actor,
                ActionType type,
                int amount,
                int betToCallBefore,
                int actorBetBefore,
                int raiseLevelAfter,
                int potBefore) {
            this.ts = ts;
            this.stage = stage == null ? "" : stage;
            this.actor = actor == null ? "" : actor;
            this.type = type;
            this.amount = amount;
            this.betToCallBefore = betToCallBefore;
            this.actorBetBefore = actorBetBefore;
            this.raiseLevelAfter = raiseLevelAfter;
            this.potBefore = potBefore;
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
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Key key = (Key) o;
            return handSeed == key.handSeed && Objects.equals(roomId, key.roomId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, handSeed);
        }
    }

    private static final ConcurrentHashMap<Key, List<ActionEvent>> EVENTS = new ConcurrentHashMap<>();

    /** 与 {@link DpHandHistoryObservedImpl#isEnabledForRoom(DpRoomBO)} 一致：有人在座即记录。 */
    static boolean isEnabledForRoom(DpRoomBO room) {
        return DpHandHistoryObservedImpl.isEnabledForRoom(room);
    }

    static void beginHand(DpRoomBO room) {
        if (room == null)
            return;
        if (!isEnabledForRoom(room))
            return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        EVENTS.put(k, Collections.synchronizedList(new ArrayList<>()));
    }

    static List<ActionEvent> snapshot(DpRoomBO room) {
        if (room == null)
            return Collections.emptyList();
        if (!isEnabledForRoom(room))
            return Collections.emptyList();
        List<ActionEvent> list = EVENTS.get(new Key(room.getRoomId(), room.getCurrentHandSeed()));
        if (list == null)
            return Collections.emptyList();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    static void clearHand(DpRoomBO room) {
        if (room == null)
            return;
        EVENTS.remove(new Key(room.getRoomId(), room.getCurrentHandSeed()));
    }

    static void recordBlind(DpRoomBO room, String nickname, boolean isSb, int amount, int potBefore) {
        if (room == null || nickname == null)
            return;
        if (!isEnabledForRoom(room))
            return;
        ActionType t = isSb ? ActionType.POST_BLIND_SB : ActionType.POST_BLIND_BB;
        append(room, new ActionEvent(System.currentTimeMillis(), "preflop", nickname, t, amount, 0, 0,
                room.getRaiseLevel(), potBefore));
    }

    static void recordFold(DpRoomBO room, DpPlayer actor, int potBefore) {
        if (room == null || actor == null)
            return;
        if (!isEnabledForRoom(room))
            return;
        append(room, new ActionEvent(System.currentTimeMillis(), room.getCurrentStage(), actor.getNickname(),
                ActionType.FOLD, 0, room.getCurrentBetToCall(), actor.getBet(), room.getRaiseLevel(), potBefore));
    }

    static void recordBetLikeAction(DpRoomBO room,
            DpPlayer actor,
            int amount,
            int betToCallBefore,
            int actorBetBefore,
            int potBefore,
            boolean becameAllIn,
            boolean isRaise) {
        if (room == null || actor == null)
            return;
        if (!isEnabledForRoom(room))
            return;
        ActionType t;
        if (amount <= 0) {
            // amount==0：要么 check（无人下注），要么 call(0) 的一种实现（看 betToCallBefore 与 actorBetBefore）
            t = (betToCallBefore <= actorBetBefore) ? ActionType.CHECK : ActionType.CALL;
        } else if (becameAllIn) {
            t = ActionType.ALL_IN;
        } else if (isRaise) {
            t = ActionType.RAISE;
        } else {
            // 非 raise：在 betToCallBefore==0 时就是 bet；否则就是 call
            t = (betToCallBefore <= actorBetBefore) ? ActionType.BET : ActionType.CALL;
        }

        append(room, new ActionEvent(System.currentTimeMillis(), room.getCurrentStage(), actor.getNickname(),
                t, amount, betToCallBefore, actorBetBefore, room.getRaiseLevel(), potBefore));
    }

    private static void append(DpRoomBO room, ActionEvent e) {
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        List<ActionEvent> list = EVENTS.computeIfAbsent(k, kk -> Collections.synchronizedList(new ArrayList<>()));
        list.add(e);
    }
}
