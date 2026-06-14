package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcTagDecisionTraceSupportTest {

    @Test
    void traceEligible_ruleArchetypesAllStreets() {
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot(DpNpcEngine.BotType.TAG, "preflop"));
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot(DpNpcEngine.BotType.TAG, "flop"));
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_TAG_1", "turn"));

        for (DpNpcEngine.BotType type : new DpNpcEngine.BotType[] {
                DpNpcEngine.BotType.FISH,
                DpNpcEngine.BotType.LAG,
                DpNpcEngine.BotType.NIT,
                DpNpcEngine.BotType.CALL,
                DpNpcEngine.BotType.MANIAC
        }) {
            assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot(type, "preflop"), type.name());
            assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot(type, "flop"), type.name());
            assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot(type, "turn"), type.name());
            assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot(type, "river"), type.name());
        }
    }

    @Test
    void traceEligible_byNicknamePrefix() {
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_FISH_2", "preflop"));
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_FISH_2", "river"));
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_LAG_1", "flop"));
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_NIT_1", "turn"));
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_CALL_1", "river"));
        assertTrue(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_MANIAC_1", "flop"));
        assertFalse(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_LLM_1", "preflop"));
        assertFalse(DpNpcTagDecisionTraceSupport.isTraceEligibleRuleBot("BOT_LLM_1", "flop"));
    }
}
