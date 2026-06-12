package com.example.mgdemoplus.npc.strategypro.l1;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.eval.DpNpcHandClassifier;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcHardConstraintsTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void mustNotFold_nutsAndFreeCard() {
        assertTrue(DpNpcHardConstraints.mustNotFold(
                DpNpcMadeHandCategory.ROCKET, null, 50, 0.90, false));
        assertTrue(DpNpcHardConstraints.mustNotFold(
                DpNpcMadeHandCategory.FULL_HOUSE, null, 200, 0.80, false));
        assertTrue(DpNpcHardConstraints.mustNotFold(
                DpNpcMadeHandCategory.HIGH_CARD, null, 0, 0.10, false));
        assertFalse(DpNpcHardConstraints.mustNotFold(
                DpNpcMadeHandCategory.HIGH_CARD, null, 50, 0.10, false));
    }

    @Test
    void adjustEquityForPlayingTheBoard_sharedRoyalNotCappedAt022() {
        List<String> hole = cards("2c", "3d");
        List<String> board = cards("Ah", "Kh", "Qh", "Jh", "Th");
        DpNpcHandSnapshot snap = DpNpcHandClassifier.classify(hole, board, "river");
        assertTrue(snap.playingTheBoard);
        assertEquals(DpNpcMadeHandCategory.HIGH_CARD, snap.made);
        assertTrue(snap.handStrength != null && snap.handStrength.rankCategory >= 9);

        double eqHu = DpNpcHardConstraints.adjustEquityForPlayingTheBoard(snap, 0.22, 1);
        double eq3w = DpNpcHardConstraints.adjustEquityForPlayingTheBoard(snap, 0.22, 2);

        assertTrue(eqHu >= 0.49, "shared royal HU chop equity should be ~0.5, got " + eqHu);
        assertTrue(eq3w >= 0.32, "shared royal 3-way chop equity should be ~0.33, got " + eq3w);
    }

    @Test
    void multiwayFoldBoost_weakHandFacingBigBetExceedsHu() {
        double hu = DpNpcHardConstraints.multiwayFoldBoost(
                DpNpcMadeHandCategory.HIGH_CARD, 1, 0.5, 0.55);
        double mw = DpNpcHardConstraints.multiwayFoldBoost(
                DpNpcMadeHandCategory.HIGH_CARD, 2, 0.5, 0.55);
        assertTrue(mw > hu, "3-way high card facing big bet should fold more than HU");
    }

    @Test
    void royalFlushFacingOverbet_neverFoldsAcrossArchetypes() {
        String[] bots = {"BOT_TAG_1", "BOT_NIT_1", "BOT_LAG_1", "BOT_MANIAC_1", "BOT_FISH_1", "BOT_CALL_1"};
        for (String nick : bots) {
            int folds = countFoldsOverSeeds(nick, buildRoyalFlushOverbetRoom(nick), 120);
            assertEquals(0, folds, nick + " must never fold nuts facing overbet");
        }
    }

    @Test
    void boardRoyalFlushFacingBet_neverFolds() {
        DpRoomBO room = buildBoardRoyalFacingBetRoom();
        int folds = countFoldsOverSeeds("BOT_MANIAC_1", room, 120);
        assertEquals(0, folds, "playing-the-board shared nuts must not fold");
    }

    @Test
    void threeWayTptkFacingTwoThirdsPot_increasesFoldBoost() {
        double huBoost = DpNpcHardConstraints.multiwayFoldBoost(
                DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, 1, 0.5, 0.40);
        double mwBoost = DpNpcHardConstraints.multiwayFoldBoost(
                DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, 2, 0.5, 0.40);
        assertTrue(mwBoost > huBoost + 0.08,
                "3-way TPTK facing ~2/3 pot should get materially higher fold boost");
    }

    @Test
    void threeWayTptkFacingTwoThirdsPot_foldsMoreThanHeadsUp() {
        int huFolds = countFoldsOverSeeds("BOT_NIT_1", buildTptkFacingBetRoom(1), 200);
        int mwFolds = countFoldsOverSeeds("BOT_NIT_1", buildTptkFacingBetRoom(2), 200);
        assertTrue(mwFolds > huFolds,
                "3-way TPTK facing 2/3 pot should fold more than HU (hu=" + huFolds + ", mw=" + mwFolds + ")");
    }

    private static int countFoldsOverSeeds(String nickname, DpRoomBO roomTemplate, int trials) {
        int folds = 0;
        for (int i = 0; i < trials; i++) {
            DpRoomBO room = cloneRoom(roomTemplate);
            room.setCurrentHandSeed(9000L + i);
            DpPlayer hero = room.getPlayers().stream()
                    .filter(p -> nickname.equals(p.getNickname()))
                    .findFirst()
                    .orElseThrow();
            room.setCurrentActorIndex(room.getPlayers().indexOf(hero));
            BotAction act = DpNpcEngine.decideActionIfReady(room, hero);
            if (act != null && act.getType() == BotActionType.FOLD) {
                folds++;
            }
        }
        return folds;
    }

    private static DpRoomBO buildRoyalFlushOverbetRoom(String heroNick) {
        DpRoomBO room = basePostflopRoom("river", 300, 250);
        room.setCommunityCards(cards("Qh", "Jh", "Th", "2c", "3d"));
        seatVillain(room, "VILLAIN_1", 40);
        DpPlayer hero = seatHero(room, heroNick, 1000, 0);
        hero.setHoleCards(cards("Ah", "Kh"));
        return room;
    }

    private static DpRoomBO buildBoardRoyalFacingBetRoom() {
        DpRoomBO room = basePostflopRoom("river", 400, 300);
        room.setCommunityCards(cards("Ah", "Kh", "Qh", "Jh", "Th"));
        seatVillain(room, "VILLAIN_1", 300);
        DpPlayer hero = seatHero(room, "BOT_MANIAC_1", 1000, 0);
        hero.setHoleCards(cards("2c", "3d"));
        return room;
    }

    private static DpRoomBO buildTptkFacingBetRoom(int extraVillains) {
        DpRoomBO room = basePostflopRoom("flop", 300, 200);
        room.setCommunityCards(cards("Ks", "7h", "2d"));
        for (int i = 0; i < extraVillains; i++) {
            seatVillain(room, "VILLAIN_" + (i + 1), 200);
        }
        DpPlayer hero = seatHero(room, "BOT_NIT_1", 500, 0);
        hero.setHoleCards(cards("Ah", "Kd"));
        return room;
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
        v.setHoleCards(cards("9c", "8d"));
        room.getPlayers().add(v);
    }

    private static List<String> cards(String... tokens) {
        return Arrays.stream(tokens)
                .flatMap(t -> Arrays.stream(t.split("_")))
                .filter(s -> !s.isBlank())
                .map(DpNpcHardConstraintsTest::toFullCard)
                .collect(Collectors.toList());
    }

    private static String toFullCard(String token) {
        if (token.contains("_")) {
            return token;
        }
        char suit = Character.toLowerCase(token.charAt(token.length() - 1));
        String rank = token.substring(0, token.length() - 1);
        if ("T".equalsIgnoreCase(rank)) {
            rank = "10";
        }
        String suitName = switch (suit) {
            case 'h' -> "hearts";
            case 'd' -> "diamonds";
            case 'c' -> "clubs";
            case 's' -> "spades";
            default -> throw new IllegalArgumentException("unknown suit: " + suit);
        };
        return suitName + "_" + rank;
    }

    private static DpRoomBO cloneRoom(DpRoomBO src) {
        DpRoomBO room = basePostflopRoom(src.getCurrentStage(), src.getPot(), src.getCurrentBetToCall());
        room.setCommunityCards(src.getCommunityCards());
        for (DpPlayer p : src.getPlayers()) {
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
}
