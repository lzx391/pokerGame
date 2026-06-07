package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;

/**
 * 疯狗（MANIAC）：翻后主要依赖 equityEst + 12 档粗分。
 */
public final class DpNpcManiacStrategy {
    private DpNpcManiacStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcLooseAggroPostflop.decide(
                p, BotType.MANIAC, DpNpcLooseAggroPostflop.LooseProfile.MANIAC);
    }
}
