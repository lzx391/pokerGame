package com.example.mgdemoplus.npc;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcCustomBotTest {

    @BeforeEach
    void disableRuleThinkForImmediateDecisionTests() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void customNicknameParsingExcludesLlmAndRuleType() {
        assertTrue(DpNpcEngine.isCustomBotNickname("BOT_CUSTOM"));
        assertTrue(DpNpcEngine.isCustomBotNickname("BOT_CUSTOM_7"));
        assertTrue(DpNpcEngine.isBotNickname("BOT_CUSTOM_7"));
        assertFalse(DpNpcEngine.isLlmBotNickname("BOT_CUSTOM_7"));
        assertNull(DpNpcEngine.getBotTypeByNickname("BOT_CUSTOM_7"));
        assertEquals("BOT_CUSTOM_12", DpNpcEngine.customBotNickname(12));
    }

    @Test
    void seatedCustomProfileDecidesOnFlopAndDiffersFromTagCbet() {
        DpRoomBO room = buildFlopFacingBetRoom();
        room.setCurrentBetToCall(0);

        DpPlayer aggressiveCustom = seatBot(room, "BOT_CUSTOM_1");
        new CustomNpcStyleSnapshot(0.55, 0.90, 0.95, 0.70, 0.20, 0.10).copyTo(aggressiveCustom);

        DpPlayer tag = seatBot(room, "BOT_TAG_1");

        int customBets = 0;
        int tagBets = 0;
        for (int i = 0; i < 100; i++) {
            room.setCurrentHandSeed(2000L + i);
            room.setCurrentActorIndex(0);
            BotAction customAct = DpNpcEngine.decideActionIfReady(room, aggressiveCustom);
            assertNotNull(customAct, "CUSTOM bot with profile should decide on flop");
            if (customAct.getType() == BotActionType.RAISE || customAct.getType() == BotActionType.ALL_IN) {
                customBets++;
            }

            room.setCurrentActorIndex(1);
            BotAction tagAct = DpNpcEngine.decideActionIfReady(room, tag);
            assertNotNull(tagAct, "TAG bot should decide on flop");
            if (tagAct.getType() == BotActionType.RAISE || tagAct.getType() == BotActionType.ALL_IN) {
                tagBets++;
            }
        }
        assertTrue(customBets > tagBets, "high cbet CUSTOM should lead/bet more than TAG when checked to");
    }

    @Test
    void highFoldToPressureCustomFoldsMoreThanLooseCustom() {
        DpRoomBO room = buildFlopFacingBetRoom();
        DpPlayer tight = seatBot(room, "BOT_CUSTOM_1");
        new CustomNpcStyleSnapshot(0.24, 0.76, 0.82, 0.36, 0.18, 0.95).copyTo(tight);
        DpPlayer loose = seatBot(room, "BOT_CUSTOM_2");
        new CustomNpcStyleSnapshot(0.64, 0.05, 0.10, 0.02, 0.96, 0.05).copyTo(loose);

        int tightFolds = 0;
        int looseFolds = 0;
        for (int i = 0; i < 100; i++) {
            room.setCurrentHandSeed(5000L + i);
            room.setCurrentActorIndex(0);
            BotAction tightAct = DpNpcEngine.decideActionIfReady(room, tight);
            if (tightAct != null && tightAct.getType() == BotActionType.FOLD) {
                tightFolds++;
            }
            room.setCurrentActorIndex(1);
            BotAction looseAct = DpNpcEngine.decideActionIfReady(room, loose);
            if (looseAct != null && looseAct.getType() == BotActionType.FOLD) {
                looseFolds++;
            }
        }
        assertTrue(tightFolds > looseFolds, "higher foldToPressure should fold more often on same spot");
    }

    private static DpRoomBO buildFlopFacingBetRoom() {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage("flop");
        room.setPot(120);
        room.setCurrentBetToCall(40);
        room.setBigBlindChips(10);
        room.setCommunityCards(List.of("Ah", "Kd", "7c"));
        return room;
    }

    private static DpPlayer seatBot(DpRoomBO room, String nickname) {
        DpPlayer bot = new DpPlayer();
        bot.setNickname(nickname);
        bot.setChips(500);
        bot.setBet(0);
        bot.setHoleCards(List.of("2h", "3d"));
        bot.setFold(false);
        bot.setAllIn(false);
        bot.setLeftThisHand(false);
        room.getPlayers().add(bot);
        return bot;
    }
}
