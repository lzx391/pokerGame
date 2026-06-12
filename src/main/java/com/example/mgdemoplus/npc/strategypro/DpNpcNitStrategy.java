package com.example.mgdemoplus.npc.strategypro;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.strategypro.postflop.nit.DpNpcNitPostflopStrategy;

/**
 * 极紧（NIT）：翻前统一矩阵 + 独立翻后策略。
 */
public final class DpNpcNitStrategy {
    private DpNpcNitStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcNitPostflopStrategy.decide(p);
    }
}
