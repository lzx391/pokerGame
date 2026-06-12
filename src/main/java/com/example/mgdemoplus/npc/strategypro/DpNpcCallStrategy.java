package com.example.mgdemoplus.npc.strategypro;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.strategypro.postflop.call.DpNpcCallPostflopStrategy;

/**
 * 跟注站（CALL）：翻前宽 defend + 翻后纯跟注站（§4.2）。
 */
public final class DpNpcCallStrategy {
    private DpNpcCallStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcCallPostflopStrategy.decide(p);
    }
}
