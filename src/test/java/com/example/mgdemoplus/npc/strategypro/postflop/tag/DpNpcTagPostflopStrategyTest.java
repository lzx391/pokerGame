package com.example.mgdemoplus.npc.strategypro.postflop.tag;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.NpcEvalTestSupport;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcTagPostflopStrategyTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void tptkDryFlopCbetsAtLeastSeventyPercent() {
        DpRoomBO probe = buildTptkDryFlopNoBetRoom();
        DpPlayer probeHero = findHero(probe);
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, probeHero);
        assertEquals(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, snap.made);

        int bets = 0;
        int nulls = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildTptkDryFlopNoBetRoom();
            room.setCurrentHandSeed(5000L + i);
            DpPlayer hero = findHero(room);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act == null) {
                nulls++;
            } else if (act.getType() == BotActionType.RAISE) {
                bets++;
            }
        }
        assertEquals(0, nulls, "decideActionIfReady must return an action");
        assertTrue(bets >= 140,
                "TAG TPTK dry flop should cbet >= 70%, got " + (bets * 100.0 / 200) + "% (" + bets + "/200)");
    }

    @Test
    void middlePairFacingBigBetFoldsOften() {
        int folds = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildMiddlePairFacingBigBetRoom();
            room.setCurrentHandSeed(6000L + i);
            DpPlayer hero = findHero(room);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertTrue(folds >= 80,
                "TAG middle pair facing big bet should fold often, got " + folds + "/200");
    }

    @Test
    void tripsWithGiveUpPlanDoesNotFoldFacingBet() {
        int folds = 0;
        for (int i = 0; i < 120; i++) {
            DpRoomBO room = buildTripsGiveUpFacingBetRoom();
            room.setCurrentHandSeed(7000L + i);
            DpPlayer hero = findHero(room);
            hero.setNpcHandPlanType(HandPlanType.GIVE_UP.name());
            hero.setNpcHandPlanMaxBarrels(0);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertEquals(0, folds, "TRIPS must not fold due to GIVE_UP plan (L1 + TAG guard)");
    }

    private static DpRoomBO buildTptkDryFlopNoBetRoom() {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, "BOT_TAG_1", 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kd"));
        return room;
    }

    private static DpRoomBO buildMiddlePairFacingBigBetRoom() {
        DpRoomBO room = basePostflopRoom("flop", 300, 200);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_7c"));
        seatVillain(room, "VILLAIN_1", 200);
        DpPlayer hero = seatHero(room, "BOT_TAG_1", 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("7h_6d"));
        return room;
    }

    private static DpRoomBO buildTripsGiveUpFacingBetRoom() {
        DpRoomBO room = basePostflopRoom("flop", 400, 250);
        room.setCommunityCards(NpcEvalTestSupport.board("7h_7d_2c"));
        seatVillain(room, "VILLAIN_1", 250);
        DpPlayer hero = seatHero(room, "BOT_TAG_1", 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("7s_7c"));
        return room;
    }

    private static DpPlayer findHero(DpRoomBO room) {
        return room.getPlayers().stream()
                .filter(p -> "BOT_TAG_1".equals(p.getNickname()))
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
