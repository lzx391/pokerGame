package com.example.mgdemoplus.npc.strategypro;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.strategypro.postflop.maniac.DpNpcManiacPostflopStrategy;

/**
 * 疯狗（MANIAC）：翻后极少 fold、高频 raise/all-in。
 */
public final class DpNpcManiacStrategy {
    private DpNpcManiacStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcManiacPostflopStrategy.decide(p);
    }
}
