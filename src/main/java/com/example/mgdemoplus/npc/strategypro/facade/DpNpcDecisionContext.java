package com.example.mgdemoplus.npc.strategypro.facade;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;

/**
 * 封装 {@link DpNpcRuleDecisionParams} + {@link DpNpcEngine.StyleProfile} + {@link DpNpcEngine.BotType}，
 * 供 {@link DpNpcStrategyFacade} 统一决策入口使用。
 */
public final class DpNpcDecisionContext {

    public final DpNpcRuleDecisionParams params;
    public final DpNpcEngine.StyleProfile style;
    public final DpNpcEngine.BotType botType;
    public final boolean customBot;

    private DpNpcDecisionContext(
            DpNpcRuleDecisionParams params,
            DpNpcEngine.StyleProfile style,
            DpNpcEngine.BotType botType,
            boolean customBot) {
        this.params = params;
        this.style = style;
        this.botType = botType;
        this.customBot = customBot;
    }

    public static DpNpcDecisionContext ofPreset(
            DpNpcRuleDecisionParams params,
            DpNpcEngine.StyleProfile style,
            DpNpcEngine.BotType botType) {
        return new DpNpcDecisionContext(params, style, botType, false);
    }

    public static DpNpcDecisionContext ofCustom(
            DpNpcRuleDecisionParams params,
            DpNpcEngine.StyleProfile style) {
        return new DpNpcDecisionContext(params, style, null, true);
    }
}
