package com.example.mgdemoplus.npc.strategypro.postflop.maniac;

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

class DpNpcManiacPostflopStrategyTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void airDryFlopCbetsAtLeastEightyPercent() {
        DpRoomBO probe = buildAirDryFlopNoBetRoom("BOT_MANIAC_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_MANIAC_1"));
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, snap.made);

        int bets = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildAirDryFlopNoBetRoom("BOT_MANIAC_1");
            room.setCurrentHandSeed(11000L + i);
            DpPlayer hero = findHero(room, "BOT_MANIAC_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && (act.getType() == BotActionType.RAISE || act.getType() == BotActionType.ALL_IN)) {
                bets++;
            }
        }
        assertTrue(bets >= 160,
                "MANIAC air dry flop should bet >= 80%, got " + (bets * 100.0 / 200) + "% (" + bets + "/200)");
    }

    @Test
    void airFacingBetFoldsAtMostTenPercent() {
        int folds = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildAirFacingHalfPotRoom("BOT_MANIAC_1");
            room.setCurrentHandSeed(11100L + i);
            DpPlayer hero = findHero(room, "BOT_MANIAC_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertTrue(folds <= 20,
                "MANIAC air facing bet should fold <= 10%, got " + (folds * 100.0 / 200) + "% (" + folds + "/200)");
    }

    @Test
    void lowSprFacingBetJamsOften() {
        int jams = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildLowSprFacingBetRoom("BOT_MANIAC_1");
            room.setCurrentHandSeed(11200L + i);
            DpPlayer hero = findHero(room, "BOT_MANIAC_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.ALL_IN) {
                jams++;
            }
        }
        assertTrue(jams >= 80,
                "MANIAC low SPR facing bet should all-in often, got " + jams + "/200");
    }

    @Test
    void maniacFoldsLessThanLagOnAirFacingBet() {
        int maniacFolds = 0;
        int lagFolds = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO maniacRoom = buildAirFacingHalfPotRoom("BOT_MANIAC_1");
            maniacRoom.setCurrentHandSeed(11300L + i);
            DpPlayer maniacHero = findHero(maniacRoom, "BOT_MANIAC_1");
            maniacRoom.setCurrentActorIndex(maniacRoom.getPlayers().indexOf(maniacHero));
            BotAction maniacAct = DpNpcEngine.decideActionIfReady(maniacRoom, maniacHero);
            if (maniacAct != null && maniacAct.getType() == BotActionType.FOLD) {
                maniacFolds++;
            }

            DpRoomBO lagRoom = buildAirFacingHalfPotRoom("BOT_LAG_1");
            lagRoom.setCurrentHandSeed(11300L + i);
            DpPlayer lagHero = findHero(lagRoom, "BOT_LAG_1");
            lagRoom.setCurrentActorIndex(lagRoom.getPlayers().indexOf(lagHero));
            BotAction lagAct = DpNpcEngine.decideActionIfReady(lagRoom, lagHero);
            if (lagAct != null && lagAct.getType() == BotActionType.FOLD) {
                lagFolds++;
            }
        }
        assertTrue(maniacFolds < lagFolds,
                "MANIAC should fold less than LAG on air facing bet: MANIAC="
                        + maniacFolds + "/200 LAG=" + lagFolds + "/200");
    }

    @Test
    void tripsWithGiveUpPlanDoesNotFoldFacingBet() {
        int folds = 0;
        for (int i = 0; i < 120; i++) {
            DpRoomBO room = buildTripsGiveUpFacingBetRoom("BOT_MANIAC_1");
            room.setCurrentHandSeed(11400L + i);
            DpPlayer hero = findHero(room, "BOT_MANIAC_1");
            hero.setNpcHandPlanType(HandPlanType.GIVE_UP.name());
            hero.setNpcHandPlanMaxBarrels(0);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertEquals(0, folds, "TRIPS must not fold (L1 + MANIAC guard)");
    }

    private static DpRoomBO buildAirDryFlopNoBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("9c_3d"));
        return room;
    }

    private static DpRoomBO buildAirFacingHalfPotRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 200, 100);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_7c"));
        seatVillain(room, "VILLAIN_1", 100);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("2c_3d"));
        return room;
    }

    private static DpRoomBO buildLowSprFacingBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 180, 60);
        room.setCommunityCards(NpcEvalTestSupport.board("Qs_9h_4d"));
        seatVillain(room, "VILLAIN_1", 60);
        DpPlayer hero = seatHero(room, heroNick, 120, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Jc_Td"));
        return room;
    }

    private static DpRoomBO buildTripsGiveUpFacingBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 400, 250);
        room.setCommunityCards(NpcEvalTestSupport.board("7h_7d_2c"));
        seatVillain(room, "VILLAIN_1", 250);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
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
        room.getPlayers().add(v);
    }
}
