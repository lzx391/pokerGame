package com.example.mgdemoplus.npc.strategypro.postflop.lag;

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

class DpNpcLagPostflopStrategyTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void tptkDryFlopCbetsAtLeastSixtyFivePercent() {
        DpRoomBO probe = buildTptkDryFlopNoBetRoom("BOT_LAG_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_LAG_1"));
        assertEquals(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, snap.made);

        int bets = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildTptkDryFlopNoBetRoom("BOT_LAG_1");
            room.setCurrentHandSeed(11000L + i);
            DpPlayer hero = findHero(room, "BOT_LAG_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.RAISE) {
                bets++;
            }
        }
        assertTrue(bets >= 130,
                "LAG TPTK dry flop should cbet >= 65%, got " + (bets * 100.0 / 200) + "% (" + bets + "/200)");
    }

    @Test
    void highCardNoDrawFacingBigBetFoldsMoreThanTag() {
        DpRoomBO lagProbe = buildHighCardNoDrawFacingBigBetRoom("BOT_LAG_1");
        DpNpcHandSnapshot lagSnap = DpNpcEngine.estimateCurrentHandSnapshot(
                lagProbe, findHero(lagProbe, "BOT_LAG_1"));
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, lagSnap.made);
        assertTrue(!DpNpcPostflopFormula.hasSemiBluffDraw(lagSnap.draw),
                "fixture must be HIGH_CARD without semi-bluff draw");

        int lagFolds = 0;
        int tagFolds = 0;
        int maniacFolds = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO lagRoom = buildHighCardNoDrawFacingBigBetRoom("BOT_LAG_1");
            lagRoom.setCurrentHandSeed(11100L + i);
            DpPlayer lagHero = findHero(lagRoom, "BOT_LAG_1");
            lagRoom.setCurrentActorIndex(lagRoom.getPlayers().indexOf(lagHero));
            BotAction lagAct = DpNpcEngine.decideActionIfReady(lagRoom, lagHero);
            if (lagAct != null && lagAct.getType() == BotActionType.FOLD) {
                lagFolds++;
            }

            DpRoomBO tagRoom = buildHighCardNoDrawFacingBigBetRoom("BOT_TAG_1");
            tagRoom.setCurrentHandSeed(11100L + i);
            DpPlayer tagHero = findHero(tagRoom, "BOT_TAG_1");
            tagRoom.setCurrentActorIndex(tagRoom.getPlayers().indexOf(tagHero));
            BotAction tagAct = DpNpcEngine.decideActionIfReady(tagRoom, tagHero);
            if (tagAct != null && tagAct.getType() == BotActionType.FOLD) {
                tagFolds++;
            }

            DpRoomBO maniacRoom = buildHighCardNoDrawFacingBigBetRoom("BOT_MANIAC_1");
            maniacRoom.setCurrentHandSeed(11100L + i);
            DpPlayer maniacHero = findHero(maniacRoom, "BOT_MANIAC_1");
            maniacRoom.setCurrentActorIndex(maniacRoom.getPlayers().indexOf(maniacHero));
            BotAction maniacAct = DpNpcEngine.decideActionIfReady(maniacRoom, maniacHero);
            if (maniacAct != null && maniacAct.getType() == BotActionType.FOLD) {
                maniacFolds++;
            }
        }
        assertTrue(lagFolds >= 100,
                "LAG air facing big bet should fold >= 50%, got " + lagFolds + "/200");
        assertTrue(lagFolds >= tagFolds,
                "LAG should fold air at least as often as TAG: LAG=" + lagFolds + "/200 TAG=" + tagFolds + "/200");
        assertTrue(lagFolds > maniacFolds,
                "LAG should fold air more than MANIAC (预埋): LAG=" + lagFolds + "/200 MANIAC=" + maniacFolds + "/200");
    }

    @Test
    void flushDrawFacingBetSemiBluffRaisesOften() {
        DpRoomBO probe = buildFlushDrawFacingBetRoom("BOT_LAG_1");
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(probe, findHero(probe, "BOT_LAG_1"));
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, snap.made);
        assertTrue(DpNpcPostflopFormula.hasSemiBluffDraw(snap.draw),
                "fixture must be HIGH_CARD with semi-bluff draw");

        int raises = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildFlushDrawFacingBetRoom("BOT_LAG_1");
            room.setCurrentHandSeed(11200L + i);
            DpPlayer hero = findHero(room, "BOT_LAG_1");
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.RAISE) {
                raises++;
            }
        }
        assertTrue(raises >= 40,
                "LAG flush draw facing bet should semi-bluff raise >= 20%, got "
                        + (raises * 100.0 / 200) + "% (" + raises + "/200)");
    }

    @Test
    void flopAirNoBetCbetsOften() {
        int cbets = 0;
        for (int i = 0; i < 200; i++) {
            DpRoomBO room = buildHighCardDryFlopNoBetRoom("BOT_LAG_1");
            room.setCurrentHandSeed(11300L + i);
            DpPlayer hero = findHero(room, "BOT_LAG_1");
            hero.setNpcHandPlanType(HandPlanType.BLUFF.name());
            hero.setNpcHandPlanMaxBarrels(2);
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.RAISE) {
                cbets++;
            }
        }
        assertTrue(cbets >= 100,
                "LAG flop air no bet should cbet >= 50%, got " + (cbets * 100.0 / 200) + "% (" + cbets + "/200)");
    }

    private static DpRoomBO buildTptkDryFlopNoBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kd"));
        return room;
    }

    private static DpRoomBO buildHighCardNoDrawFacingBigBetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("flop", 500, 260);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 260);
        DpPlayer hero = seatHero(room, heroNick, 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Qc_Jd"));
        hero.setNpcHandPlanType(HandPlanType.POT_CONTROL.name());
        hero.setNpcHandPlanMaxBarrels(1);
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
