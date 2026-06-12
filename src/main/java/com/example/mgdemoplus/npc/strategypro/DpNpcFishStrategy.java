package com.example.mgdemoplus.npc.strategypro;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.strategypro.postflop.fish.DpNpcFishPostflopStrategy;

/**
 * ????FISH???????? + ???????
 */
public final class DpNpcFishStrategy {
    private DpNpcFishStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcFishPostflopStrategy.decide(p);
    }
}
