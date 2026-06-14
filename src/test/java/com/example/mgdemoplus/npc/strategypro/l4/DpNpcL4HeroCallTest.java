package com.example.mgdemoplus.npc.strategypro.l4;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.VillainRangeTier;
import com.example.mgdemoplus.npc.eval.DpBoardTexture;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.NpcEvalTestSupport;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcL4HeroCallTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void riverHighCardHeroCallProbabilityWithLowCredibility() {
        DpNpcRuleDecisionParams p = buildRiverHighCardParams(BotType.LAG, new Random());
        DpUtilSmartContext ctx = buildCtx(0.12, 1, ActionCredibility.LOW, 0.55);
        double prob = DpNpcHeroCall.heroCallProbability(
                p, "river", p.callAmount,
                DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, ctx);
        assertEquals(0.15, prob, 1e-9,
                "LAG river hero call with low credibility: base 0.10 × 1.5 bonus");
    }

    @Test
    void riverHighCardHeroCallWithLowCredibility() {
        // Random(4096).nextDouble() ≈ 0.099 < prob 0.15
        DpNpcRuleDecisionParams p = buildRiverHighCardParams(BotType.LAG, new Random(4096L));
        DpUtilSmartContext ctx = buildCtx(0.12, 1, ActionCredibility.LOW, 0.55);
        assertTrue(DpNpcHeroCall.shouldHeroCall(
                p, "river", p.callAmount,
                DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, ctx),
                "fixed seed should hit hero call when roll < probability");
    }

    @Test
    void bigBetExcludesHeroCall() {
        DpNpcRuleDecisionParams p = buildRiverHighCardParams(BotType.LAG, new Random());
        p = new DpNpcRuleDecisionParams(
                bigBetRoom(400, 300),
                p.bot,
                p.type,
                p.chips,
                300,
                0.6,
                p.position,
                p.stageForNpc,
                p.random,
                p.boardDanger,
                p.handSnapshot,
                p.preflopTight,
                p.aggression,
                p.bluffFrequency,
                p.callStation,
                p.stealBlindFrequency,
                p.checkRaiseFear);
        DpUtilSmartContext ctx = buildCtx(0.12, 1, ActionCredibility.LOW, 0.55);
        assertFalse(DpNpcHeroCall.shouldHeroCall(
                p, "river", 300,
                DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, ctx),
                "bet > 0.65 pot should not hero call");
    }

    @Test
    void multiwayThreePlusExcludesHeroCall() {
        DpNpcRuleDecisionParams p = buildRiverHighCardParams(BotType.TAG, new Random());
        DpUtilSmartContext ctx = buildCtx(0.12, 3, ActionCredibility.LOW, 0.55);
        assertFalse(DpNpcHeroCall.shouldHeroCall(
                p, "river", p.callAmount,
                DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, ctx),
                "3+ villains should not hero call");
    }

    @Test
    void callTypeNeverHeroCalls() {
        DpNpcRuleDecisionParams p = buildRiverHighCardParams(BotType.CALL, new Random());
        DpUtilSmartContext ctx = buildCtx(0.12, 1, ActionCredibility.LOW, 0.55);
        assertFalse(DpNpcHeroCall.shouldHeroCall(
                p, "river", p.callAmount,
                DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, ctx),
                "CALL archetype should not use extra hero call");
    }

    private static DpNpcRuleDecisionParams buildRiverHighCardParams(BotType type, Random random) {
        DpRoomBO room = baseRiverRoom(200, 80);
        DpPlayer bot = seatBot(room, "BOT_TEST", 1000, 0, type);
        bot.setHoleCards(NpcEvalTestSupport.hole("2h_7d"));
        DpNpcHandSnapshot snap = DpNpcHandSnapshot.postflop(
                DpNpcMadeHandCategory.HIGH_CARD,
                DpNpcDrawCategory.NONE,
                null,
                false,
                false,
                true,
                false,
                false,
                DpBoardTexture.analyze(room.getCommunityCards()));
        return new DpNpcRuleDecisionParams(
                room, bot, type, bot.getChips(), 80, 0.08,
                DpNpcEngine.TablePosition.LATE, "river", random,
                DpNpcEngine.BoardDanger.DRY, snap,
                0.5, 0.7, 0.3, 0.3, 0.5, 0.2);
    }

    private static DpUtilSmartContext buildCtx(
            double equityEst,
            int activeVillains,
            ActionCredibility credibility,
            double showdownBluffiness) {
        return new DpUtilSmartContext(
                null,
                null,
                VillainRangeTier.LOOSE,
                credibility,
                showdownBluffiness,
                0.15,
                equityEst,
                null,
                activeVillains,
                null,
                0,
                0,
                0,
                null);
    }

    private static DpRoomBO baseRiverRoom(int pot, int betToCall) {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage("river");
        room.setPot(pot);
        room.setCurrentBetToCall(betToCall);
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_9h_4d_2c_Jc"));
        return room;
    }

    private static DpRoomBO bigBetRoom(int pot, int betToCall) {
        DpRoomBO room = baseRiverRoom(pot, betToCall);
        return room;
    }

    private static DpPlayer seatBot(DpRoomBO room, String nickname, int chips, int bet, BotType type) {
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
