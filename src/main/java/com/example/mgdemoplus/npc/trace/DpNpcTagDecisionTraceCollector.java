package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import com.example.mgdemoplus.npc.trace.model.DpNpcPreflopMatrixSnapshot;
import com.example.mgdemoplus.npc.trace.model.DpNpcPreflopRaiseMeta;
import com.example.mgdemoplus.npc.trace.model.DpNpcTraceStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ThreadLocal：当前 TAG action 的 step 构建器。 */
public final class DpNpcTagDecisionTraceCollector {
    private static final ThreadLocal<DpNpcTagDecisionTraceCollector> CURRENT = new ThreadLocal<>();

    private DpRoomBO room;
    private DpPlayer bot;
    private final List<DpNpcTraceStep> steps = new ArrayList<>();
    private DpNpcPreflopMatrixSnapshot preflopMatrix;
    private DpNpcPreflopMatrixSnapshot preflopMatrixSecondary;
    private DpNpcPreflopRaiseMeta raiseMeta;
    private int nextSeq = 1;

    private DpNpcTagDecisionTraceCollector() {
    }

    public static DpNpcTagDecisionTraceCollector current() {
        return CURRENT.get();
    }

    public static void begin(DpRoomBO room, DpPlayer bot) {
        if (!DpNpcTagDecisionTraceStore.ENABLED || room == null || bot == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector c = new DpNpcTagDecisionTraceCollector();
        c.room = room;
        c.bot = bot;
        CURRENT.set(c);
    }

    public static void step(String phase, String code, String message, Map<String, Object> data) {
        DpNpcTagDecisionTraceCollector c = CURRENT.get();
        if (c == null) {
            return;
        }
        c.steps.add(new DpNpcTraceStep(c.nextSeq++, phase, code, message, data));
    }

    public static void attachMatrix(DpNpcPreflopMatrixSnapshot snapshot) {
        DpNpcTagDecisionTraceCollector c = CURRENT.get();
        if (c == null || snapshot == null) {
            return;
        }
        c.preflopMatrix = snapshot;
    }

    public static void attachSecondaryMatrix(DpNpcPreflopMatrixSnapshot snapshot) {
        DpNpcTagDecisionTraceCollector c = CURRENT.get();
        if (c == null || snapshot == null) {
            return;
        }
        c.preflopMatrixSecondary = snapshot;
    }

    public static void attachRaiseMeta(DpNpcPreflopRaiseMeta meta) {
        DpNpcTagDecisionTraceCollector c = CURRENT.get();
        if (c == null || meta == null) {
            return;
        }
        c.raiseMeta = meta;
    }

    public static DpNpcActionTrace build(DpNpcEngine.BotAction action) {
        DpNpcTagDecisionTraceCollector c = CURRENT.get();
        if (c == null || c.room == null || c.bot == null) {
            return null;
        }
        long handSeed = c.room.getCurrentHandSeed();
        int actionSeq = DpNpcTagDecisionTraceStore.peekNextActionSeq(c.room);
        DpNpcActionTrace trace = new DpNpcActionTrace();
        trace.actionId = DpNpcTagDecisionTraceSupport.formatActionId(handSeed, actionSeq);
        trace.actionSeq = actionSeq;
        trace.actorNickname = c.bot.getNickname();
        trace.seatIndex = DpNpcTagDecisionTraceSupport.seatIndex(c.room.getPlayers(), c.bot);
        trace.street = c.room.getCurrentStage() != null ? c.room.getCurrentStage() : "";
        trace.timestampMs = System.currentTimeMillis();
        trace.finalAction = DpNpcTagDecisionTraceSupport.toFinalAction(action);
        trace.holeCards = DpNpcTagDecisionTraceSupport.holeCardsArray(c.bot);
        trace.steps = new ArrayList<>(c.steps);
        trace.preflopMatrix = c.preflopMatrix;
        trace.preflopMatrixSecondary = c.preflopMatrixSecondary;
        trace.raiseMeta = c.raiseMeta;
        trace.steps.add(new DpNpcTraceStep(
                c.nextSeq,
                "RESULT",
                "COMMIT",
                "final=" + trace.finalAction.type + (trace.finalAction.amount > 0 ? " amount=" + trace.finalAction.amount : ""),
                null));
        return trace;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 便捷：单键 data map。 */
    public static Map<String, Object> dataOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        if (kv == null) {
            return m;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
