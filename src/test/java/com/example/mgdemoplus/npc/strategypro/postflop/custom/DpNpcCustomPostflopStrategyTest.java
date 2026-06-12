package com.example.mgdemoplus.npc.strategypro.postflop.custom;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.CustomNpcStyleSnapshot;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.NpcEvalTestSupport;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcCustomPostflopStrategyTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void highCbetFreqLeadsMuchMoreThanLowCbetFreq() {
        DpRoomBO probe = buildTptkDryFlopNoBetRoom(0.95);
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe));
        assertEquals(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, snap.made);

        int highCbetBets = countLeadBets(0.95, 0.76, 8000L);
        int lowCbetBets = countLeadBets(0.10, 0.76, 9000L);

        double diffPct = (highCbetBets - lowCbetBets) * 100.0 / 200;
        assertTrue(diffPct >= 40.0,
                "high cbetFreq (0.95) vs low (0.10) lead diff should be >= 40%, got "
                        + diffPct + "% (high=" + highCbetBets + " low=" + lowCbetBets + ")");
    }

    @Test
    void highCallStationNeverRaisesFacingBet() {
        int raises = 0;
        for (int i = 0; i < 100; i++) {
            DpRoomBO room = buildTptkFacingBetRoom(0.96);
            room.setCurrentHandSeed(10000L + i);
            DpPlayer hero = findHero(room);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && (act.getType() == BotActionType.RAISE || act.getType() == BotActionType.ALL_IN)) {
                raises++;
            }
        }
        assertEquals(0, raises, "callStation 0.96 should block all raises when facing bet");
    }

    @Test
    void highFoldToPressureFoldsMoreThanLowOnMiddlePair() {
        int tightFolds = countFolds(0.95, 11000L);
        int looseFolds = countFolds(0.05, 12000L);
        assertTrue(tightFolds > looseFolds,
                "foldToPressure 0.95 should fold more than 0.05 on middle pair: tight="
                        + tightFolds + " loose=" + looseFolds);
        assertTrue(looseFolds < 100,
                "low foldToPressure should not always fold middle pair, got " + looseFolds + "/100");
    }

    private static int countLeadBets(double cbetFreq, double pfr, long seedBase) {
        int bets = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildTptkDryFlopNoBetRoom(cbetFreq);
            applyStyle(room, cbetFreq, pfr, 0.36, 0.18, 0.22);
            room.setCurrentHandSeed(seedBase + i);
            DpPlayer hero = findHero(room);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && (act.getType() == BotActionType.RAISE || act.getType() == BotActionType.ALL_IN)) {
                bets++;
            }
        }
        return bets;
    }

    private static int countFolds(double foldToPressure, long seedBase) {
        int folds = 0;
        for (int i = 0; i < 100; i++) {
            DpRoomBO room = buildMiddlePairFacingBetRoom(foldToPressure);
            room.setCurrentHandSeed(seedBase + i);
            DpPlayer hero = findHero(room);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        return folds;
    }

    private static DpRoomBO buildTptkDryFlopNoBetRoom(double cbetFreq) {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, "BOT_CUSTOM_1", 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kd"));
        applyStyle(room, cbetFreq, 0.76, 0.36, 0.18, 0.22);
        return room;
    }

    private static DpRoomBO buildTptkFacingBetRoom(double callStation) {
        DpRoomBO room = basePostflopRoom("flop", 300, 200);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 200);
        DpPlayer hero = seatHero(room, "BOT_CUSTOM_1", 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kd"));
        applyStyle(room, 0.82, 0.76, 0.36, callStation, 0.22);
        return room;
    }

    private static DpRoomBO buildMiddlePairFacingBetRoom(double foldToPressure) {
        DpRoomBO room = basePostflopRoom("flop", 200, 100);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_7c"));
        seatVillain(room, "VILLAIN_1", 100);
        DpPlayer hero = seatHero(room, "BOT_CUSTOM_1", 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("7h_6d"));
        applyStyle(room, 0.55, 0.40, 0.50, 0.20, foldToPressure);
        return room;
    }

    private static void applyStyle(
            DpRoomBO room,
            double cbetFreq,
            double pfr,
            double bluffFreq,
            double callStation,
            double foldToPressure) {
        DpPlayer hero = findHero(room);
        new CustomNpcStyleSnapshot(0.55, pfr, cbetFreq, bluffFreq, callStation, foldToPressure).copyTo(hero);
    }

    private static DpPlayer findHero(DpRoomBO room) {
        return room.getPlayers().stream()
                .filter(p -> "BOT_CUSTOM_1".equals(p.getNickname()))
                .findFirst()
                .orElseThrow();
    }

    private static DpRoomBO basePostflopRoom(String stage, int pot, int betToCall) {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage(stage);
        room.setPot(pot);
        room.setCurrentBetToCall(betToCall);
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        return room;
    }

    private static DpPlayer seatHero(DpRoomBO room, String nickname, int chips, int bet) {
        DpPlayer bot = new DpPlayer();
        bot.setNickname(nickname);
        bot.setChips(chips);
        bot.setBet(bet);
        bot.setFold(false);
        bot.setAllIn(false);
        bot.setLeftThisHand(false);
        room.getPlayers().add(bot);
        return bot;
    }

    private static void seatVillain(DpRoomBO room, String nickname, int bet) {
        DpPlayer v = new DpPlayer();
        v.setNickname(nickname);
        v.setChips(1000);
        v.setBet(bet);
        v.setFold(false);
        v.setAllIn(false);
        v.setLeftThisHand(false);
        v.setHoleCards(NpcEvalTestSupport.hole("9c_8d"));
        room.getPlayers().add(v);
    }
}
