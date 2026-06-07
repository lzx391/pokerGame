package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;

/**
 * 紧凶（TAG）：翻后 12 档公式化决策。
 */
public final class DpNpcTagStrategy {
    private DpNpcTagStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcTightAggroPostflop.decide(
                p,
                BotType.TAG,
                DpNpcTightAggroPostflop.PersonalityProfile.TAG);
    }
}
