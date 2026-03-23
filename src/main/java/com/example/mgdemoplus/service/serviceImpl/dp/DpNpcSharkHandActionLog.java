package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shark 专用“逐街动作日志”（实验版）。
 *
 * <p>目标：补齐目前缺失的逐街动作轨迹，让 Shark 能更准确地识别：
 * - 谁是 flop/turn/river 的激进者（bet/raise）
 * - 谁在 turn/river 常放弃（raise 后放弃 / 只打一枪）
 * - 未来可扩展：c-bet、double barrel、check-raise、下注尺度偏好等</p>
 *
 * <p>为了不影响其他 NPC/前端：该日志只在桌上存在 BOT_Shark 时启用，
 * 且只保留“当前这一手”的内存态记录；结算后会清空。</p>
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
        final String stage;     // preflop/flop/turn/river
        final String actor;     // nickname
        final ActionType type;
        final int amount;       // 本次投入金额（check/fold 为 0）
        final int betToCallBefore;
        final int actorBetBefore;
        final int raiseLevelAfter;
        final int potBefore;    // 发生该动作前的底池（用于按尺度分桶：bet/pot）

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

    private static final ConcurrentHashMap<Key, List<ActionEvent>> EVENTS = new ConcurrentHashMap<>();

    static boolean isEnabledForRoom(DpRoom room) {
        if (room == null || room.getPlayers() == null) return false;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null) continue;
            if (DpNpcEngine.SHARK_BOT_NICKNAME.equals(p.getNickname())) {
                return true;
            }
        }
        return false;
    }

    static void beginHand(DpRoom room) {
        if (room == null) return;
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        EVENTS.put(k, Collections.synchronizedList(new ArrayList<>()));
    }

    static List<ActionEvent> snapshot(DpRoom room) {
        if (room == null) return Collections.emptyList();
        if (!isEnabledForRoom(room)) return Collections.emptyList();
        List<ActionEvent> list = EVENTS.get(new Key(room.getRoomId(), room.getCurrentHandSeed()));
        if (list == null) return Collections.emptyList();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    static void clearHand(DpRoom room) {
        if (room == null) return;
        EVENTS.remove(new Key(room.getRoomId(), room.getCurrentHandSeed()));
    }

    static void recordBlind(DpRoom room, String nickname, boolean isSb, int amount, int potBefore) {
        if (room == null || nickname == null) return;
        if (!isEnabledForRoom(room)) return;
        ActionType t = isSb ? ActionType.POST_BLIND_SB : ActionType.POST_BLIND_BB;
        append(room, new ActionEvent(System.currentTimeMillis(), "preflop", nickname, t, amount, 0, 0, room.getRaiseLevel(), potBefore));
    }

    static void recordFold(DpRoom room, DpPlayer actor, int potBefore) {
        if (room == null || actor == null) return;
        if (!isEnabledForRoom(room)) return;
        append(room, new ActionEvent(System.currentTimeMillis(), room.getCurrentStage(), actor.getNickname(),
                ActionType.FOLD, 0, room.getCurrentBetToCall(), actor.getBet(), room.getRaiseLevel(), potBefore));
    }

    static void recordBetLikeAction(DpRoom room,
                                    DpPlayer actor,
                                    int amount,
                                    int betToCallBefore,
                                    int actorBetBefore,
                                    int potBefore,
                                    boolean becameAllIn,
                                    boolean isRaise) {
        if (room == null || actor == null) return;
        if (!isEnabledForRoom(room)) return;
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

    private static void append(DpRoom room, ActionEvent e) {
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        List<ActionEvent> list = EVENTS.computeIfAbsent(k, kk -> Collections.synchronizedList(new ArrayList<>()));
        list.add(e);
    }
}

