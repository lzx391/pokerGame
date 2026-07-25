package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.eval.DpNpcPreflopHandGrouper.HoleInfo;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTraceSummary;

import java.util.ArrayList;
import java.util.List;

/** 规则 NPC 决策 trace 辅助：bot 识别、actionId、BotAction → JSON 字段。 */
public final class DpNpcTagDecisionTraceSupport {
    private DpNpcTagDecisionTraceSupport() {
    }

    private static final String[] RANK_CHARS = {"2", "3", "4", "5", "6", "7", "8", "9", "T", "J", "Q", "K", "A"};

    public static boolean isTagBot(String nickname) {
        return DpNpcEngine.getBotTypeByNickname(nickname) == DpNpcEngine.BotType.TAG;
    }

    /**
     * 是否应采集决策 trace：TAG/Fish/LAG/NIT/CALL/Maniac 翻前+翻后；CUSTOM/LLM 不采集。
     */
    public static boolean isTraceEligibleRuleBot(DpNpcEngine.BotType type, String stage) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case TAG, FISH, LAG, NIT, CALL, MANIAC -> true;
            default -> false;
        };
    }

    /** 昵称 → 是否 trace  eligible（含 legacy 映射）。 */
    public static boolean isTraceEligibleRuleBot(String nickname, String stage) {
        return isTraceEligibleRuleBot(DpNpcEngine.getBotTypeByNickname(nickname), stage);
    }

    public static String formatActionId(long handSeed, int actionSeq) {
        return handSeed + "-" + actionSeq;
    }

    public static DpNpcActionTraceSummary.FinalAction toFinalAction(DpNpcEngine.BotAction action) {
        if (action == null) {
            return new DpNpcActionTraceSummary.FinalAction("CALL_OR_CHECK", 0);
        }
        return new DpNpcActionTraceSummary.FinalAction(action.getType().name(), action.getAmount());
    }

    public static String[] holeCardsArray(DpPlayer bot) {
        if (bot == null || bot.getHoleCards() == null || bot.getHoleCards().isEmpty()) {
            return new String[0];
        }
        return bot.getHoleCards().toArray(new String[0]);
    }

    public static String heroHandLabel(HoleInfo hole) {
        if (hole == null || !hole.valid) {
            return "";
        }
        int hi = Math.max(hole.r1, hole.r2);
        int lo = Math.min(hole.r1, hole.r2);
        String rHi = rankChar(hi);
        String rLo = rankChar(lo);
        if (hi == lo) {
            return rHi + rLo;
        }
        return rHi + rLo + (hole.suited ? "s" : "o");
    }

    public static String rankChar(int rank) {
        if (rank < 2 || rank > 14) {
            return "?";
        }
        return RANK_CHARS[rank - 2];
    }

    public static int seatIndex(List<DpPlayer> players, DpPlayer bot) {
        if (players == null || bot == null) {
            return -1;
        }
        return players.indexOf(bot);
    }

    public static String resolveDealerNickname(List<DpPlayer> players) {
        if (players == null) {
            return "";
        }
        for (DpPlayer p : players) {
            if (p != null && p.isDealer()) {
                return p.getNickname() != null ? p.getNickname() : "";
            }
        }
        return "";
    }

    public static List<DpNpcActionTraceSummary> toSummaries(List<com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace> full) {
        List<DpNpcActionTraceSummary> out = new ArrayList<>();
        if (full == null) {
            return out;
        }
        for (com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace a : full) {
            if (a == null) {
                continue;
            }
            DpNpcActionTraceSummary s = new DpNpcActionTraceSummary();
            s.actionId = a.actionId;
            s.actionSeq = a.actionSeq;
            s.actorNickname = a.actorNickname;
            s.seatIndex = a.seatIndex;
            s.street = a.street;
            s.timestampMs = a.timestampMs;
            s.finalAction = a.finalAction;
            s.holeCards = a.holeCards;
            out.add(s);
        }
        return out;
    }
}
