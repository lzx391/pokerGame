package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;

/**
 * 松凶（LAG）：翻后 12 档 + 组合听半诈唬。
 */
public final class DpNpcLagStrategy {
    private DpNpcLagStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcLooseAggroPostflop.decide(
                p, BotType.LAG, DpNpcLooseAggroPostflop.LooseProfile.LAG);
    }
}
