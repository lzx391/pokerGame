package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;

/**
 * 极紧（NIT）：翻后 12 档公式化决策，比 TAG 更保守。
 */
public final class DpNpcNitStrategy {
    private DpNpcNitStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcTightAggroPostflop.decide(
                p,
                BotType.NIT,
                DpNpcTightAggroPostflop.PersonalityProfile.NIT);
    }
}
