package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.DpPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逐街动作内存日志：flop/turn/river 谁在激进、谁在街口弃牌等，供当局统计与牌谱分析。
 * 仅保留当前这一手的记录，结算后清空。
 */
final class DpNpcStreetActionLog {
    private DpNpcStreetActionLog() {
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
        final String stage;
        final String actor;
        final ActionType type;
        final int amount;
        final int betToCallBefore;
        final int actorBetBefore;
        final int raiseLevelAfter;
        final int potBefore;

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
            t = (betToCallBefore <= actorBetBefore) ? ActionType.CHECK : ActionType.CALL;
        } else if (becameAllIn) {
            t = ActionType.ALL_IN;
        } else if (isRaise) {
            t = ActionType.RAISE;
        } else {
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
