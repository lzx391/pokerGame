package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.TablePosition;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;

import java.util.Random;

/**
 * {@link com.example.mgdemoplus.npc.engine.DpNpcEngine#decideBotAction} 在分发到各规则 NPC 策略类时的入参快照。
 * mood 不参与决策，见 {@code dp.npc.mood} 与 {@link com.example.mgdemoplus.npc.mood.DpNpcMoodProperties}。
 */
public final class DpNpcRuleDecisionParams {
    public final DpRoomBO room;
    public final DpPlayer bot;
    public final BotType type;
    public final int chips;
    public final int callAmount;
    public final double callRatio;
    public final TablePosition position;
    /** 与引擎内 {@code stageForNpc} 一致 */
    public final String stageForNpc;
    public final Random random;
    public final BoardDanger boardDanger;
    public final SimpleStrength strength;
    public final double preflopTight;
    public final double aggression;
    public final double bluffFrequency;
    public final double callStation;
    public final double stealBlindFrequency;
    public final double checkRaiseFear;

    public DpNpcRuleDecisionParams(
            DpRoomBO room,
            DpPlayer bot,
            BotType type,
            int chips,
            int callAmount,
            double callRatio,
            TablePosition position,
            String stageForNpc,
            Random random,
            BoardDanger boardDanger,
            SimpleStrength strength,
            double preflopTight,
            double aggression,
            double bluffFrequency,
            double callStation,
            double stealBlindFrequency,
            double checkRaiseFear) {
        this.room = room;
        this.bot = bot;
        this.type = type;
        this.chips = chips;
        this.callAmount = callAmount;
        this.callRatio = callRatio;
        this.position = position;
        this.stageForNpc = stageForNpc;
        this.random = random;
        this.boardDanger = boardDanger;
        this.strength = strength;
        this.preflopTight = preflopTight;
        this.aggression = aggression;
        this.bluffFrequency = bluffFrequency;
        this.callStation = callStation;
        this.stealBlindFrequency = stealBlindFrequency;
        this.checkRaiseFear = checkRaiseFear;
    }
}
