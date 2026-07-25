package com.example.mgdemoplus.npc.strategypro.postflop.tag;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.NpcEvalTestSupport;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.npc.strategypro.facade.DpNpcDecisionContext;
import com.example.mgdemoplus.npc.strategypro.l1.DpNpcHardConstraints;
import com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTraceCollector;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import com.example.mgdemoplus.npc.trace.model.DpNpcTraceStep;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DpNpcTagPostflopTraceTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @AfterEach
    void clearCollector() {
        DpNpcTagDecisionTraceCollector.clear();
    }

    @Test
    void foldFacingBet_traceContainsFoldRollAndCommit() {
        for (long seed = 6000L; seed < 6200L; seed++) {
            TraceResult result = decideTag(buildMiddlePairFacingBigBetRoom(), seed, null);
            if (result.action.getType() != BotActionType.FOLD) {
                continue;
            }
            assertTrue(hasStep(result.trace, "POSTFLOP_SPOT"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "FOLD_ROLL"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "FOLD"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "COMMIT"), "seed=" + seed);
            assertEquals("FOLD", result.trace.finalAction.type, "seed=" + seed);
            return;
        }
        fail("expected at least one fold seed in range 6000-6199");
    }

    @Test
    void callFacingBet_traceContainsFoldRollMissAndRaiseRollMiss() {
        for (long seed = 6000L; seed < 6200L; seed++) {
            TraceResult result = decideTag(buildMiddlePairFacingBigBetRoom(), seed, null);
            if (result.action.getType() != BotActionType.CALL_OR_CHECK) {
                continue;
            }
            assertTrue(hasStep(result.trace, "FOLD_ROLL_MISS"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "RAISE_ROLL_MISS"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "POSTFLOP_ACTION"), "seed=" + seed);
            assertFalse(hasStep(result.trace, "call facing bet (raiseProb miss)"), "seed=" + seed);
            return;
        }
        fail("expected at least one call seed in range 6000-6199");
    }

    @Test
    void giveUpFacingBet_traceContainsGiveUpBoostAndCallPath() {
        for (long seed = 7000L; seed < 7200L; seed++) {
            TraceResult result = decideTag(buildTopPairWeakFacingBetRoom(), seed, HandPlanType.GIVE_UP);
            if (result.action.getType() != BotActionType.CALL_OR_CHECK) {
                continue;
            }
            DpNpcTraceStep spot = findStep(result.trace, "POSTFLOP_SPOT");
            assertNotNull(spot);
            assertEquals("GIVE_UP", spot.data.get("plan"));
            assertTrue(hasStep(result.trace, "GIVE_UP_FOLD_BOOST"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "FOLD_ROLL_MISS"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "RAISE_ROLL_MISS"), "seed=" + seed);
            return;
        }
        fail("expected GIVE_UP call seed in range 7000-7199");
    }

    @Test
    void noBetValueBet_traceContainsValueBetRoll() {
        for (long seed = 5000L; seed < 5200L; seed++) {
            TraceResult result = decideTag(buildTptkDryFlopNoBetRoom(), seed, null);
            if (result.action.getType() != BotActionType.RAISE) {
                continue;
            }
            assertTrue(hasStep(result.trace, "VALUE_BET_ROLL"), "seed=" + seed);
            assertTrue(hasStep(result.trace, "POSTFLOP_ACTION"), "seed=" + seed);
            return;
        }
        fail("expected value bet seed in range 5000-5199");
    }

    @Test
    void postflopSpot_contextDataEnriched() {
        TraceResult result = decideTag(buildTptkDryFlopNoBetRoom(), 5001L, HandPlanType.VALUE);
        DpNpcTraceStep spot = findStep(result.trace, "POSTFLOP_SPOT");
        assertNotNull(spot);
        assertNotNull(spot.data.get("made"));
        assertNotNull(spot.data.get("madeLabel"));
        assertNotNull(spot.data.get("draw"));
        assertNotNull(spot.data.get("drawLabel"));
        assertNotNull(spot.data.get("equityEst"));
        assertNotNull(spot.data.get("potOdds"));
        assertNotNull(spot.data.get("commitFactor"));
        assertNotNull(spot.data.get("commitThreshold"));
        assertTrue(spot.message.contains("顶对强踢") || spot.message.contains("TPTK")
                || "TOP_PAIR_TOP_KICKER".equals(spot.data.get("made")));
    }

    @Test
    void l1BlockFold_traceContainsMadeAndEquityEst() {
        DpRoomBO room = buildTripsFacingBetRoom();
        DpPlayer hero = findHero(room);
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(room, hero);
        DpNpcRuleDecisionParams params = new DpNpcRuleDecisionParams(
                room,
                hero,
                DpNpcEngine.BotType.TAG,
                hero.getChips(),
                250,
                0.25,
                DpNpcEngine.TablePosition.MIDDLE,
                "flop",
                new Random(1L),
                DpNpcEngine.BoardDanger.DRY,
                snap,
                0.24,
                0.7,
                0.3,
                0.18,
                0.5,
                0.2);
        DpNpcEngine.StyleProfile style = DpNpcEngine.styleProfileFromValues(
                0.24, 0.76, 0.82, 0.36, 0.18, 0.22);
        DpNpcDecisionContext ctx = DpNpcDecisionContext.ofPreset(params, style, DpNpcEngine.BotType.TAG);

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        BotAction overridden = DpNpcHardConstraints.applyOrOverride(
                ctx, new BotAction(BotActionType.FOLD, 0));
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(overridden);

        assertEquals(BotActionType.CALL_OR_CHECK, overridden.getType());
        DpNpcTraceStep l1 = findStep(trace, "L1_BLOCK_FOLD");
        assertNotNull(l1);
        assertNotNull(l1.data.get("made"));
        assertNotNull(l1.data.get("madeLabel"));
        assertNotNull(l1.data.get("equityEst"));
        assertTrue(l1.data.get("made").toString().startsWith("TRIPS")
                || l1.data.get("made").toString().equals("QUADS"));
    }

    private static TraceResult decideTag(DpRoomBO template, long seed, HandPlanType plan) {
        DpRoomBO room = cloneRoom(template);
        DpPlayer hero = findHero(room);
        if (plan != null) {
            hero.setNpcHandPlanType(plan.name());
            hero.setNpcHandPlanMaxBarrels(0);
        }
        room.setCurrentActorIndex(room.getPlayers().indexOf(hero));

        int callAmount = Math.max(0, room.getCurrentBetToCall() - hero.getBet());
        double callRatio = hero.getChips() == 0 || callAmount >= hero.getChips()
                ? 1.0
                : (callAmount * 1.0 / hero.getChips());
        DpNpcHandSnapshot snap = DpNpcEngine.estimateCurrentHandSnapshot(room, hero);
        Random random = new Random(seed);
        DpNpcRuleDecisionParams params = new DpNpcRuleDecisionParams(
                room,
                hero,
                DpNpcEngine.BotType.TAG,
                hero.getChips(),
                callAmount,
                callRatio,
                DpNpcEngine.TablePosition.MIDDLE,
                room.getCurrentStage(),
                random,
                DpNpcEngine.BoardDanger.DRY,
                snap,
                0.24,
                0.7,
                0.36,
                0.18,
                0.5,
                0.22);
        DpNpcEngine.StyleProfile style = DpNpcEngine.styleProfileFromValues(
                0.24, 0.76, 0.82, 0.36, 0.18, 0.22);
        DpNpcDecisionContext ctx = DpNpcDecisionContext.ofPreset(params, style, DpNpcEngine.BotType.TAG);

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        BotAction raw = DpNpcTagPostflopStrategy.decide(params);
        BotAction action = DpNpcHardConstraints.applyOrOverride(ctx, raw);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);
        assertNotNull(action);
        assertNotNull(trace);
        return new TraceResult(action, trace);
    }

    private static boolean hasStep(DpNpcActionTrace trace, String code) {
        return findStep(trace, code) != null;
    }

    private static DpNpcTraceStep findStep(DpNpcActionTrace trace, String code) {
        if (trace == null || trace.steps == null) {
            return null;
        }
        for (DpNpcTraceStep step : trace.steps) {
            if (code.equals(step.code)) {
                return step;
            }
        }
        return null;
    }

    private static DpRoomBO cloneRoom(DpRoomBO template) {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(template.isPlaying());
        room.setCurrentStage(template.getCurrentStage());
        room.setPot(template.getPot());
        room.setCurrentBetToCall(template.getCurrentBetToCall());
        room.setBigBlindChips(template.getBigBlindChips());
        room.setSmallBlindChips(template.getSmallBlindChips());
        room.setCommunityCards(template.getCommunityCards());
        for (DpPlayer p : template.getPlayers()) {
            DpPlayer copy = new DpPlayer();
            copy.setNickname(p.getNickname());
            copy.setChips(p.getChips());
            copy.setBet(p.getBet());
            copy.setFold(p.isFold());
            copy.setAllIn(p.isAllIn());
            copy.setLeftThisHand(p.isLeftThisHand());
            copy.setHoleCards(p.getHoleCards());
            copy.setNpcHandPlanType(p.getNpcHandPlanType());
            copy.setNpcHandPlanMaxBarrels(p.getNpcHandPlanMaxBarrels());
            room.getPlayers().add(copy);
        }
        return room;
    }

    private static DpRoomBO buildTptkDryFlopNoBetRoom() {
        DpRoomBO room = basePostflopRoom("flop", 150, 0);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_7h_2d"));
        seatVillain(room, "VILLAIN_1", 0);
        DpPlayer hero = seatHero(room, "BOT_TAG_1", 1000, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("Ah_Kd"));
        hero.setNpcHandPlanType(HandPlanType.VALUE.name());
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

    private static DpRoomBO buildTopPairWeakFacingBetRoom() {
        DpRoomBO room = basePostflopRoom("flop", 280, 120);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_3c"));
        seatVillain(room, "VILLAIN_1", 120);
        DpPlayer hero = seatHero(room, "BOT_TAG_1", 800, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("8h_6d"));
        return room;
    }

    private static DpRoomBO buildTripsFacingBetRoom() {
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

    private record TraceResult(BotAction action, DpNpcActionTrace trace) {
    }
}
