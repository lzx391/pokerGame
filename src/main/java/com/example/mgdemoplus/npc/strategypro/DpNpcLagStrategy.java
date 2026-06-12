package com.example.mgdemoplus.npc.strategypro;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.strategypro.postflop.lag.DpNpcLagPostflopStrategy;

/**
 * 松凶（LAG）：翻前矩阵 + 独立翻后策略。
 */
public final class DpNpcLagStrategy {
    private DpNpcLagStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcLagPostflopStrategy.decide(p);
    }
}
