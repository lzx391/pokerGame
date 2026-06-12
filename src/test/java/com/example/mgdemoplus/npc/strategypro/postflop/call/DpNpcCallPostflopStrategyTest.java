package com.example.mgdemoplus.npc.strategypro.postflop.call;

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

class DpNpcCallPostflopStrategyTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void middlePairFacingBigBetCallsMoreThanFish() {
        DpRoomBO probe = buildMiddlePairFacingBigBetRoom("BOT_CALL_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_CALL_1"));
        assertEquals(DpNpcMadeHandCategory.MIDDLE_PAIR, snap.made);

        int callCalls = 0;
        int fishCalls = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO callRoom = buildMiddlePairFacingBigBetRoom("BOT_CALL_1");
            callRoom.setCurrentHandSeed(9500L + i);
            DpPlayer callHero = findHero(callRoom, "BOT_CALL_1");
            callRoom.setCurrentActorIndex(callRoom.getPlayers().indexOf(callHero));
            BotAction callAct = DpNpcEngine.decideActionIfReady(callRoom, callHero);
            if (callAct != null && callAct.getType() == BotActionType.CALL_OR_CHECK) {
                callCalls++;
            }

            DpRoomBO fishRoom = buildMiddlePairFacingBigBetRoom("BOT_FISH_1");
            fishRoom.setCurrentHandSeed(9500L + i);
            DpPlayer fishHero = findHero(fishRoom, "BOT_FISH_1");
            fishRoom.setCurrentActorIndex(fishRoom.getPlayers().indexOf(fishHero));
            BotAction fishAct = DpNpcEngine.decideActionIfReady(fishRoom, fishHero);
            if (fishAct != null && fishAct.getType() == BotActionType.CALL_OR_CHECK) {
                fishCalls++;
            }
        }
        assertTrue(callCalls >= 170,
                "CALL middle pair facing big bet should call >= 85%, got "
                        + (callCalls * 100.0 / 200) + "% (" + callCalls + "/200)");
        assertTrue(callCalls > fishCalls,
                "CALL should call more than FISH on middle pair facing big bet: CALL="
                        + callCalls + "/200 FISH=" + fishCalls + "/200");
    }

    @Test
    void anyPairFacingBetCallsOften() {
        String[][] fixtures = {
                {"BOT_CALL_1", "8h_6d", "Ks_8d_7c", "MIDDLE_PAIR"},
                {"BOT_CALL_1", "8h_2d", "Ks_8d_7c", "BOTTOM_PAIR"},
                {"BOT_CALL_1", "Ah_Kd", "Ks_7h_2d", "TOP_PAIR_TOP_KICKER"},
        };
        for (String[] fix : fixtures) {
            int calls = 0;
            for (int i = 0; i < 200; i++) {
                DpRoomBO room = buildPairFacingBetRoom(fix[0], fix[1], fix[2]);
                room.setCurrentHandSeed(9600L + i);
                DpPlayer hero = findHero(room, fix[0]);
                room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
                BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
                if (act != null && act.getType() == BotActionType.CALL_OR_CHECK) {
                    calls++;
                }
            }
            assertTrue(calls >= 170,
                    fix[3] + " facing bet should call >= 85%, got " + calls + "/200");
        }
    }

    @Test
    void flushDrawFacingBetNeverFolds() {
        DpRoomBO probe = buildFlushDrawFacingBetRoom("BOT_CALL_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_CALL_1"));
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, snap.made);
        assertTrue(DpNpcPostflopFormula.hasSemiBluffDraw(snap.draw),
                "fixture must be HIGH_CARD with semi-bluff draw");

        int calls = 0;
        int folds = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildFlushDrawFacingBetRoom("BOT_CALL_1");
            room.setCurrentHandSeed(9700L + i);
            DpPlayer hero = findHero(room, "BOT_CALL_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.CALL_OR_CHECK) {
                calls++;
            } else if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertEquals(0, folds, "CALL must never fold flush draw facing bet");
        assertTrue(calls >= 190,
                "CALL flush draw should call >= 95%, got " + calls + "/200");
    }

    @Test
    void neverRaisesVoluntarily() {
        int raises = 0;
        for (int i = 0; i < 100; i++) {
            DpRoomBO room = buildTptkNoBetRoom("BOT_CALL_1");
            room.setCurrentHandSeed(9800L + i);
            DpPlayer hero = findHero(room, "BOT_CALL_1");
            hero.setNpcHandPlanType(HandPlanType.VALUE.name());
            hero.setNpcHandPlanMaxBarrels(3);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.RAISE) {
                raises++;
            }
        }
        assertEquals(0, raises, "CALL must never voluntarily raise (0% cbet)");
    }

    @Test
    void nutsFacingOverbetNeverFolds() {
        int folds = 0;
        for (int i = 0; i < 120; i++) {
            DpRoomBO room = buildRoyalFlushOverbetRoom("BOT_CALL_1");
            room.setCurrentHandSeed(9900L + i);
            DpPlayer hero = findHero(room, "BOT_CALL_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        assertEquals(0, folds, "CALL must never fold nuts (L1 protection)");
    }

    private static DpRoomBO buildMiddlePairFacingBigBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 300, 200);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_7c"));
        seatVillain(room, "VILLAIN_1", 200);
        DpPlayer hero = seatHero(room, heroNick, 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("8h_6d"));
        return room;
    }

    private static DpRoomBO buildPairFacingBetRoom(String heroNick, String hole, String board) {
        DpRoomBO room = basePostflopRoom("flop", 300, 150);
        room.setCommunityCards(NpcEvalTestSupport.board(board));
        seatVillain(room, "VILLAIN_1", 150);
        DpPlayer hero = seatHero(room, heroNick, 600, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole(hole));
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

    private static DpRoomBO buildTptkNoBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kd"));
        return room;
    }

    private static DpRoomBO buildRoyalFlushOverbetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("river", 300, 250);
        room.setCommunityCards(NpcEvalTestSupport.board("Qh_Jh_Th_2c_3d"));
        seatVillain(room, "VILLAIN_1", 40);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kh"));
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
