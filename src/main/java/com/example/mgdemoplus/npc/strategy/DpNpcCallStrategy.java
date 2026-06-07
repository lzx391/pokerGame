package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;

/**
 * 跟注站（CALL）：翻后 12 档 + 听牌更宽跟注。
 */
public final class DpNpcCallStrategy {
    private DpNpcCallStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcPassiveStationPostflop.decide(
                p, BotType.CALL, DpNpcPassiveStationPostflop.StationProfile.CALL);
    }
}
