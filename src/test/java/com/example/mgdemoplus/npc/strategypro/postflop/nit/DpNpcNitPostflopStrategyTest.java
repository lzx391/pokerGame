package com.example.mgdemoplus.npc.strategypro.postflop.nit;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.npc.eval.NpcEvalTestSupport;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcNitPostflopStrategyTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void tptkDryFlopCbetsAtMostThirtyPercent() {
        DpRoomBO probe = buildTptkDryFlopNoBetRoom("BOT_NIT_1");
        DpPlayer probeHero = findHero(probe, "BOT_NIT_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, probeHero);
        assertEquals(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, snap.made);

        int bets = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildTptkDryFlopNoBetRoom("BOT_NIT_1");
            room.setCurrentHandSeed(8000L + i);
            DpPlayer hero = findHero(room, "BOT_NIT_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.RAISE) {
                bets++;
            }
        }
        assertTrue(bets <= 60,
                "NIT TPTK dry flop should cbet <= 30%, got " + (bets * 100.0 / 200) + "% (" + bets + "/200)");
    }

    @Test
    void tpwkWetBoardFacingTwoThirdsPotFoldsOften() {
        DpRoomBO probe = buildTpwkWetFacingTwoThirdsPotRoom("BOT_NIT_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_NIT_1"));
        assertEquals(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER, snap.made);

        int folds = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildTpwkWetFacingTwoThirdsPotRoom("BOT_NIT_1");
            room.setCurrentHandSeed(8100L + i);
            DpPlayer hero = findHero(room, "BOT_NIT_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertTrue(folds >= 120,
                "NIT TPWK wet board facing 2/3 pot should fold >= 60%, got " + folds + "/200");
    }

    @Test
    void nitFoldsMoreThanTagOnWetBoardFacingBet() {
        int nitFolds = 0;
        int tagFolds = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO nitRoom = buildTpwkWetFacingTwoThirdsPotRoom("BOT_NIT_1");
            nitRoom.setCurrentHandSeed(8200L + i);
            DpPlayer nitHero = findHero(nitRoom, "BOT_NIT_1");
            nitRoom.setCurrentActorIndex(nitRoom.getPlayers().indexOf(nitHero));
            BotAction nitAct = DpNpcEngine.decideActionIfReady(nitRoom, nitHero);
            if (nitAct != null && nitAct.getType() == BotActionType.FOLD) {
                nitFolds++;
            }

            DpRoomBO tagRoom = buildTpwkWetFacingTwoThirdsPotRoom("BOT_TAG_1");
            tagRoom.setCurrentHandSeed(8200L + i);
            DpPlayer tagHero = findHero(tagRoom, "BOT_TAG_1");
            tagRoom.setCurrentActorIndex(tagRoom.getPlayers().indexOf(tagHero));
            BotAction tagAct = DpNpcEngine.decideActionIfReady(tagRoom, tagHero);
            if (tagAct != null && tagAct.getType() == BotActionType.FOLD) {
                tagFolds++;
            }
        }
        assertTrue(nitFolds > tagFolds,
                "NIT should fold more than TAG on wet board facing 2/3 pot: NIT="
                        + nitFolds + "/200 TAG=" + tagFolds + "/200");
    }

    @Test
    void highCardWithDrawSemiBluffsLessThanTag() {
        int nitBets = 0;
        int tagBets = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO nitRoom = buildHighCardFlushDrawDryNoBetRoom("BOT_NIT_1");
            nitRoom.setCurrentHandSeed(8300L + i);
            DpPlayer nitHero = findHero(nitRoom, "BOT_NIT_1");
            nitHero.setNpcHandPlanType(HandPlanType.BLUFF.name());
            nitHero.setNpcHandPlanMaxBarrels(2);
            roomProbeHighCardDraw(nitRoom, nitHero);
            nitRoom.setCurrentActorIndex(nitRoom.getPlayers().indexOf(nitHero));
            BotAction nitAct = DpNpcEngine.decideActionIfReady(nitRoom, nitHero);
            if (nitAct != null && nitAct.getType() == BotActionType.RAISE) {
                nitBets++;
            }

            DpRoomBO tagRoom = buildHighCardFlushDrawDryNoBetRoom("BOT_TAG_1");
            tagRoom.setCurrentHandSeed(8300L + i);
            DpPlayer tagHero = findHero(tagRoom, "BOT_TAG_1");
            tagHero.setNpcHandPlanType(HandPlanType.BLUFF.name());
            tagHero.setNpcHandPlanMaxBarrels(2);
            roomProbeHighCardDraw(tagRoom, tagHero);
            tagRoom.setCurrentActorIndex(tagRoom.getPlayers().indexOf(tagHero));
            BotAction tagAct = DpNpcEngine.decideActionIfReady(tagRoom, tagHero);
            if (tagAct != null && tagAct.getType() == BotActionType.RAISE) {
                tagBets++;
            }
        }
        assertTrue(tagBets >= 30,
                "TAG should semi-bluff sometimes on dry board+draw, got " + tagBets + "/200");
        assertTrue(nitBets < tagBets,
                "NIT should semi-bluff less than TAG with HIGH_CARD+draw: NIT="
                        + nitBets + "/200 TAG=" + tagBets + "/200");
    }

    private static void roomProbeHighCardDraw(DpRoomBO room, DpPlayer hero) {
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(room, hero);
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, snap.made);
        assertTrue(DpNpcPostflopFormula.hasSemiBluffDraw(snap.draw),
                "fixture must be HIGH_CARD with semi-bluff draw");
    }

    @Test
    void tripsDoesNotFoldFacingBet() {
        int folds = 0;
        for (int i = 0; i < 120; i++) {
            DpRoomBO room = buildTripsFacingBetRoom();
            room.setCurrentHandSeed(8400L + i);
            DpPlayer hero = findHero(room, "BOT_NIT_1");
            hero.setNpcHandPlanType(HandPlanType.GIVE_UP.name());
            hero.setNpcHandPlanMaxBarrels(0);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertEquals(0, folds, "TRIPS must not fold (L1 nut guard)");
    }

    private static DpRoomBO buildTptkDryFlopNoBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kd"));
        return room;
    }

    private static DpRoomBO buildTpwkWetFacingTwoThirdsPotRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 300, 200);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_9h_8h"));
        seatVillain(room, "VILLAIN_1", 200);
        DpPlayer hero = seatHero(room, heroNick, 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Kh_7d"));
        return room;
    }

    private static DpRoomBO buildHighCardFlushDrawDryNoBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("9c_6d_2c"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ac_8c"));
        return room;
    }

    private static DpRoomBO buildTripsFacingBetRoom() {
        DpRoomBO room = basePostflopRoom("flop", 400, 250);
        room.setCommunityCards(NpcEvalTestSupport.board("7h_7d_2c"));
        seatVillain(room, "VILLAIN_1", 250);
        DpPlayer hero = seatHero(room, "BOT_NIT_1", 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("7s_7c"));
        return room;
    }

    private static DpPlayer findHero(DpRoomBO room, String nickname) {
        return room.getPlayers().stream()
                .filter(p -> nickname.equals(p.getNickname()))
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
