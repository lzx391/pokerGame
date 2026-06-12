package com.example.mgdemoplus.npc.strategypro.facade;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.strategypro.DpNpcCallStrategy;
import com.example.mgdemoplus.npc.strategypro.DpNpcCustomStrategy;
import com.example.mgdemoplus.npc.strategypro.DpNpcFishStrategy;
import com.example.mgdemoplus.npc.strategypro.DpNpcLagStrategy;
import com.example.mgdemoplus.npc.strategypro.DpNpcManiacStrategy;
import com.example.mgdemoplus.npc.strategypro.DpNpcNitStrategy;
import com.example.mgdemoplus.npc.strategypro.DpNpcTagStrategy;
import com.example.mgdemoplus.npc.strategypro.l1.DpNpcHardConstraints;
import com.example.mgdemoplus.npc.strategypro.preflop.DpNpcUnifiedPreflopStrategy;

/**
 * Phase 1：内部仍调用迁包后的旧策略类；Wave 3 后逐 archetype 替换为独立 postflop 策略。
 */
final class DpNpcStrategyFacadeImpl implements DpNpcStrategyFacade {

    @Override
    public DpNpcEngine.BotAction decidePreflop(DpNpcDecisionContext ctx) {
        return DpNpcUnifiedPreflopStrategy.decide(
                ctx.params.room,
                ctx.params.bot,
                ctx.params.callAmount,
                ctx.params.callRatio,
                ctx.style.getVpip(),
                ctx.style.getPfr(),
                ctx.style.getCallStation(),
                ctx.style.getFoldToPressure(),
                ctx.params.random,
                ctx.botType);
    }

    @Override
    public DpNpcEngine.BotAction decidePostflop(DpNpcDecisionContext ctx) {
        DpNpcEngine.BotAction action;
        if (ctx.customBot) {
            action = DpNpcCustomStrategy.decide(ctx.params);
        } else if (ctx.botType == null) {
            action = new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        } else {
            action = switch (ctx.botType) {
                case FISH -> DpNpcFishStrategy.decide(ctx.params);
                case CALL -> DpNpcCallStrategy.decide(ctx.params);
                case LAG -> DpNpcLagStrategy.decide(ctx.params);
                case MANIAC -> DpNpcManiacStrategy.decide(ctx.params);
                case TAG -> DpNpcTagStrategy.decide(ctx.params);
                case NIT -> DpNpcNitStrategy.decide(ctx.params);
                default -> new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            };
        }
        return DpNpcHardConstraints.applyOrOverride(ctx, action);
    }
}
