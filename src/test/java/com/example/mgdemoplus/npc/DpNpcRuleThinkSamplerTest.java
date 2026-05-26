package com.example.mgdemoplus.npc;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcRuleThinkSamplerTest {

    private DpNpcRuleThinkProperties saved;

    @BeforeEach
    void saveAndReset() {
        saved = DpNpcRuleThinkSampler.currentProperties();
    }

    @AfterEach
    void restore() {
        DpNpcRuleThinkSampler.bind(saved);
    }

    @Test
    void disabledAlwaysReturnsZero() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        assertEquals(0L, DpNpcRuleThinkSampler.sampleDelayMs(props, new Random(1)));
    }

    @Test
    void snapProbabilityAlwaysZero() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setSnapProbability(1.0);
        Random rng = new Random(42);
        for (int i = 0; i < 20; i++) {
            assertEquals(0L, DpNpcRuleThinkSampler.sampleDelayMs(props, rng));
        }
    }

    @Test
    void noSnapRespectsMaxMsCap() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setSnapProbability(0.0);
        props.setFastMinMs(5000);
        props.setFastMaxMs(8000);
        props.setSlowMinMs(5000);
        props.setSlowMaxMs(8000);
        props.setMaxMs(4000);
        props.setFastWeight(1.0);
        props.setSlowWeight(0.0);
        Random rng = new Random(7);
        for (int i = 0; i < 50; i++) {
            long delay = DpNpcRuleThinkSampler.sampleDelayMs(props, rng);
            assertEquals(4000L, delay);
        }
    }

    @Test
    void decideActionIfReadySchedulesThenActs() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setSnapProbability(0.0);
        props.setFastMinMs(500);
        props.setFastMaxMs(500);
        props.setSlowMinMs(500);
        props.setSlowMaxMs(500);
        DpNpcRuleThinkSampler.bind(props);

        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage("flop");
        room.setPot(100);
        room.setCurrentBetToCall(0);
        room.setBigBlindChips(10);
        room.setCurrentHandSeed(99L);
        room.setCommunityCards(List.of("Ah", "Kd", "7c"));

        DpPlayer bot = new DpPlayer();
        bot.setNickname("BOT_TAG_1");
        bot.setChips(500);
        bot.setHoleCards(List.of("As", "Ks"));
        bot.setFold(false);
        bot.setAllIn(false);
        bot.setLeftThisHand(false);
        room.getPlayers().add(bot);
        room.setCurrentActorIndex(0);

        BotAction first = DpNpcEngine.decideActionIfReady(room, bot);
        assertNull(first);
        assertTrue(bot.getNextBotActionTime() > System.currentTimeMillis());

        bot.setNextBotActionTime(System.currentTimeMillis() - 1);
        BotAction second = DpNpcEngine.decideActionIfReady(room, bot);
        assertNotNull(second);
        assertEquals(0L, bot.getNextBotActionTime());
    }
}
