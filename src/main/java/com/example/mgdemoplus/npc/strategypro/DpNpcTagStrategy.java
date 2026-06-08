package com.example.mgdemoplus.npc.strategypro;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.strategypro.postflop.tag.DpNpcTagPostflopStrategy;

/**
 * 紧凶（TAG）：翻前统一矩阵 + 独立翻后策略。
 */
public final class DpNpcTagStrategy {
    private DpNpcTagStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcTagPostflopStrategy.decide(p);
    }
}
