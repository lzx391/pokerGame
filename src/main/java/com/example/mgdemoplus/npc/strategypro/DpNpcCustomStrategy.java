package com.example.mgdemoplus.npc.strategypro;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.strategypro.postflop.custom.DpNpcCustomPostflopStrategy;

/**
 * 自定义 NPC 翻后：六维 L5 偏移独立策略；翻前仍走 {@link preflop.DpNpcUnifiedPreflopStrategy}。
 */
public final class DpNpcCustomStrategy {
    private DpNpcCustomStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcCustomPostflopStrategy.decide(p);
    }
}
