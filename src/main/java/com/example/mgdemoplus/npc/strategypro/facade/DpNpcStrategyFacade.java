package com.example.mgdemoplus.npc.strategypro.facade;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;

/**
 * 规则 NPC 策略统一门面：翻前 / 翻后单一出口，{@link com.example.mgdemoplus.npc.engine.DpNpcEngine} 仅委托此处。
 */
public interface DpNpcStrategyFacade {

    /** 翻前统一出口 */
    DpNpcEngine.BotAction decidePreflop(DpNpcDecisionContext ctx);

    /** 翻后分 archetype（含 CUSTOM） */
    DpNpcEngine.BotAction decidePostflop(DpNpcDecisionContext ctx);
}
