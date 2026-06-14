package com.example.mgdemoplus.npc.strategypro.postflop;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.eval.NpcEvalTestSupport;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.npc.strategypro.facade.DpNpcDecisionContext;
import com.example.mgdemoplus.npc.strategypro.l1.DpNpcHardConstraints;
import com.example.mgdemoplus.npc.strategypro.postflop.call.DpNpcCallPostflopStrategy;
import com.example.mgdemoplus.npc.strategypro.postflop.fish.DpNpcFishPostflopStrategy;
import com.example.mgdemoplus.npc.strategypro.postflop.lag.DpNpcLagPostflopStrategy;
import com.example.mgdemoplus.npc.strategypro.postflop.maniac.DpNpcManiacPostflopStrategy;
import com.example.mgdemoplus.npc.strategypro.postflop.nit.DpNpcNitPostflopStrategy;
import com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTraceCollector;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import com.example.mgdemoplus.npc.trace.model.DpNpcTraceStep;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DpNpcPostflopTraceMultiArchetypeTest {

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

    @ParameterizedTest
    @EnumSource(value = BotType.class, names = {"FISH", "LAG", "NIT", "CALL", "MANIAC"})
    void facingBet_traceContainsPostflopSpotAndProbStep(BotType botType) {
        for (long seed = 8000L; seed < 8200L; seed++) {
            DpRoomBO room = botType == BotType.CALL
                    ? buildHighCardFacingBigBetRoom(botType)
                    : buildMiddlePairFacingBetRoom(botType);
            TraceResult result = decide(room, botType, seed, strategyFor(botType));
            if (result.action.getType() == BotActionType.CALL_OR_CHECK) {
                DpNpcTraceStep spot = findStep(result.trace, "POSTFLOP_SPOT");
                assertNotNull(spot, botType + " seed=" + seed);
                assertNotNull(spot.data.get("madeLabel"), botType.name());
                assertNotNull(spot.data.get("equityEst"), botType.name());
                assertTrue(
                        hasStep(result.trace, "FOLD_ROLL_MISS") || hasStep(result.trace, "FOLD_ROLL"),
                        botType + " seed=" + seed);
                assertTrue(hasStep(result.trace, "POSTFLOP_ACTION"), botType.name());
                assertTrue(hasStep(result.trace, "COMMIT"), botType.name());
                return;
            }
        }
        fail("expected call seed for " + botType + " in range 8000-8199");
    }

    private static Function<DpNpcRuleDecisionParams, BotAction> strategyFor(BotType type) {
        return switch (type) {
            case FISH -> DpNpcFishPostflopStrategy::decide;
            case LAG -> DpNpcLagPostflopStrategy::decide;
            case NIT -> DpNpcNitPostflopStrategy::decide;
            case CALL -> DpNpcCallPostflopStrategy::decide;
            case MANIAC -> DpNpcManiacPostflopStrategy::decide;
            default -> throw new IllegalArgumentException("unsupported: " + type);
        };
    }

    private static TraceResult decide(
            DpRoomBO template,
            BotType botType,
            long seed,
            Function<DpNpcRuleDecisionParams, BotAction> strategy) {
        DpRoomBO room = cloneRoom(template);
        DpPlayer hero = findHero(room, botType);
        room.setCurrentActorIndex(room.getPlayers().indexOf(hero));

        int callAmount = Math.max(0, room.getCurrentBetToCall() - hero.getBet());
        double callRatio = hero.getChips() == 0 || callAmount >= hero.getChips()
                ? 1.0
                : (callAmount * 1.0 / hero.getChips());
        Random random = new Random(seed);
        DpNpcRuleDecisionParams params = new DpNpcRuleDecisionParams(
                room,
                hero,
                botType,
                hero.getChips(),
                callAmount,
                callRatio,
                DpNpcEngine.TablePosition.MIDDLE,
                room.getCurrentStage(),
                random,
                DpNpcEngine.BoardDanger.DRY,
                DpNpcEngine.estimateCurrentHandSnapshot(room, hero),
                styleFor(botType)[0],
                styleFor(botType)[1],
                styleFor(botType)[2],
                styleFor(botType)[3],
                styleFor(botType)[4],
                styleFor(botType)[5]);
        DpNpcEngine.StyleProfile style = DpNpcEngine.styleProfileFromValues(
                styleFor(botType)[0], styleFor(botType)[1], styleFor(botType)[2],
                styleFor(botType)[3], styleFor(botType)[4], styleFor(botType)[5]);
        DpNpcDecisionContext ctx = DpNpcDecisionContext.ofPreset(params, style, botType);

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        BotAction raw = strategy.apply(params);
        BotAction action = DpNpcHardConstraints.applyOrOverride(ctx, raw);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);
        assertNotNull(action);
        assertNotNull(trace);
        return new TraceResult(action, trace);
    }

    private static double[] styleFor(BotType type) {
        return switch (type) {
            case FISH -> new double[] {0.55, 0.45, 0.20, 0.36, 0.5, 0.22};
            case LAG -> new double[] {0.28, 0.72, 0.79, 0.36, 0.5, 0.22};
            case NIT -> new double[] {0.18, 0.82, 0.28, 0.36, 0.5, 0.22};
            case CALL -> new double[] {0.50, 0.50, 0.0, 0.36, 0.5, 0.22};
            case MANIAC -> new double[] {0.15, 0.85, 0.88, 0.36, 0.5, 0.22};
            default -> new double[] {0.24, 0.76, 0.82, 0.36, 0.18, 0.22};
        };
    }

    private static DpRoomBO buildHighCardFacingBigBetRoom(BotType botType) {
        DpRoomBO room = basePostflopRoom("flop", 400, 350);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_7c"));
        seatVillain(room, "VILLAIN_1", 350);
        DpPlayer hero = seatHero(room, botNickname(botType), 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("2h_3d"));
        return room;
    }

    private static DpRoomBO buildMiddlePairFacingBetRoom(BotType botType) {
        DpRoomBO room = basePostflopRoom("flop", 300, 200);
        room.setCommunityCards(NpcEvalTestSupport.board("Ks_8d_7c"));
        seatVillain(room, "VILLAIN_1", 200);
        DpPlayer hero = seatHero(room, botNickname(botType), 500, 0);
        hero.setHoleCards(NpcEvalTestSupport.hole("7h_6d"));
        return room;
    }

    private static String botNickname(BotType type) {
        return switch (type) {
            case FISH -> "BOT_FISH_1";
            case LAG -> "BOT_LAG_1";
            case NIT -> "BOT_NIT_1";
            case CALL -> "BOT_CALL_1";
            case MANIAC -> "BOT_MANIAC_1";
            default -> "BOT_TAG_1";
        };
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
            room.getPlayers().add(copy);
        }
        return room;
    }

    private static DpPlayer findHero(DpRoomBO room, BotType botType) {
        String nickname = botNickname(botType);
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

    private record TraceResult(BotAction action, DpNpcActionTrace trace) {
    }
}
