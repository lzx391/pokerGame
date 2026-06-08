package com.example.mgdemoplus.npc.strategypro.postflop.fish;

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

class DpNpcFishPostflopStrategyTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void middlePairFacingHalfPotCallsOften() {
        DpRoomBO probe = buildMiddlePairFacingHalfPotRoom("BOT_FISH_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_FISH_1"));
        assertEquals(DpNpcMadeHandCategory.MIDDLE_PAIR, snap.made);

        int calls = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildMiddlePairFacingHalfPotRoom("BOT_FISH_1");
            room.setCurrentHandSeed(9000L + i);
            DpPlayer hero = findHero(room, "BOT_FISH_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.CALL_OR_CHECK) {
                calls++;
            }
        }
        assertTrue(calls >= 140,
                "FISH middle pair facing 1/2 pot should call >= 70%, got " + (calls * 100.0 / 200) + "% (" + calls + "/200)");
    }

    @Test
    void flushDrawFacingBetCallsOften() {
        DpRoomBO probe = buildFlushDrawFacingBetRoom("BOT_FISH_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_FISH_1"));
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, snap.made);
        assertTrue(DpNpcPostflopFormula.hasSemiBluffDraw(snap.draw),
                "fixture must be HIGH_CARD with semi-bluff draw");

        int calls = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildFlushDrawFacingBetRoom("BOT_FISH_1");
            room.setCurrentHandSeed(9100L + i);
            DpPlayer hero = findHero(room, "BOT_FISH_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.CALL_OR_CHECK) {
                calls++;
            }
        }
        assertTrue(calls >= 120,
                "FISH flush draw facing bet should call often, got " + calls + "/200");
    }

    @Test
    void fishCallsMoreThanNitOnMiddlePairFacingHalfPot() {
        int fishCalls = 0;
        int nitCalls = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO fishRoom = buildMiddlePairFacingHalfPotRoom("BOT_FISH_1");
            fishRoom.setCurrentHandSeed(9200L + i);
            DpPlayer fishHero = findHero(fishRoom, "BOT_FISH_1");
            fishRoom.setCurrentActorIndex(fishRoom.getPlayers().indexOf(fishHero));
            BotAction fishAct = DpNpcEngine.decideActionIfReady(fishRoom, fishHero);
            if (fishAct != null && fishAct.getType() == BotActionType.CALL_OR_CHECK) {
                fishCalls++;
            }

            DpRoomBO nitRoom = buildMiddlePairFacingHalfPotRoom("BOT_NIT_1");
            nitRoom.setCurrentHandSeed(9200L + i);
            DpPlayer nitHero = findHero(nitRoom, "BOT_NIT_1");
            nitRoom.setCurrentActorIndex(nitRoom.getPlayers().indexOf(nitHero));
            BotAction nitAct = DpNpcEngine.decideActionIfReady(nitRoom, nitHero);
            if (nitAct != null && nitAct.getType() == BotActionType.CALL_OR_CHECK) {
                nitCalls++;
            }
        }
        assertTrue(fishCalls > nitCalls,
                "FISH should call more than NIT on middle pair facing 1/2 pot: FISH="
                        + fishCalls + "/200 NIT=" + nitCalls + "/200");
    }

    @Test
    void flopHighCardOccasionallyDonks() {
        DpRoomBO probe = buildHighCardDryFlopNoBetRoom("BOT_FISH_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_FISH_1"));
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, snap.made);

        int donks = 0;
        int nulls = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildHighCardDryFlopNoBetRoom("BOT_FISH_1");
            room.setCurrentHandSeed(i * 131L + 3L);
            DpPlayer hero = findHero(room, "BOT_FISH_1");
            hero.setNpcHandPlanType(HandPlanType.BLUFF.name());
            hero.setNpcHandPlanMaxBarrels(2);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act == null) {
                nulls++;
            } else if (act.getType() == BotActionType.RAISE) {
                donks++;
            }
        }
        assertEquals(0, nulls, "decideActionIfReady must return an action");
        assertTrue(donks >= 5 && donks <= 30,
                "FISH flop donk bluff should be occasional (<=15%), got " + (donks * 100.0 / 200) + "% (" + donks + "/200)");
    }

    @Test
    void riverHighCardRarelyBluffs() {
        int bluffs = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildHighCardDryRiverNoBetRoom("BOT_FISH_1");
            room.setCurrentHandSeed(6000L + i);
            DpPlayer hero = findHero(room, "BOT_FISH_1");
            hero.setNpcHandPlanType(HandPlanType.BLUFF.name());
            hero.setNpcHandPlanMaxBarrels(2);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.RAISE) {
                bluffs++;
            }
        }
        assertTrue(bluffs <= 15,
                "FISH river should rarely bluff, got " + bluffs + "/200");
    }

    private static DpRoomBO buildMiddlePairFacingHalfPotRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 300, 200);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_7c"));
        seatVillain(room, "VILLAIN_1", 200);
        DpPlayer hero = seatHero(room, heroNick, 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("8h_6d"));
        return room;
    }

    private static DpRoomBO buildFlushDrawFacingBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 200, 100);
        room.setCommunityCards(NpcEvalTestSupport.board("9c_6d_2c"));
        seatVillain(room, "VILLAIN_1", 100);
        DpPlayer hero = seatHero(room, heroNick, 800, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ac_8c"));
        return room;
    }

    private static DpRoomBO buildHighCardDryFlopNoBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Qc_Jd"));
        return room;
    }

    private static DpRoomBO buildHighCardDryRiverNoBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("river", 400, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d_5c_3s"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Qc_Jd"));
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
