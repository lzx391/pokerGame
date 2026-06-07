package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;

/**
 * 娱乐鱼（FISH）：翻后 12 档 + 听牌宽跟。
 */
public final class DpNpcFishStrategy {
    private DpNpcFishStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        return DpNpcPassiveStationPostflop.decide(
                p, BotType.FISH, DpNpcPassiveStationPostflop.StationProfile.FISH);
    }
}
