package com.example.mgdemoplus.npc;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;
import com.example.mgdemoplus.npc.strategypro.preflop.DpNpcUnifiedPreflopStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcPreflopDefendTest {

    private static final double FISH_VPIP = 0.52;
    private static final double FISH_PFR = 0.15;
    private static final double FISH_CALL_STATION = 0.74;
    private static final double FISH_FOLD = 0.38;

    private static final double TAG_VPIP = 0.24;
    private static final double TAG_PFR = 0.76;
    private static final double TAG_CALL_STATION = 0.18;
    private static final double TAG_FOLD = 0.22;

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void fishFacingMinRaiseDefendsPocketSevensAndSuited98() {
        assertDefends("hearts_7", "diamonds_7", DpNpcEngine.BotType.FISH);
        assertDefends("hearts_9", "hearts_8", DpNpcEngine.BotType.FISH);
    }

    @Test
    void tagFacingMinRaiseDefendsSomeBroadway() {
        assertDefends("hearts_K", "diamonds_Q", DpNpcEngine.BotType.TAG);
        assertDefends("hearts_J", "diamonds_10", DpNpcEngine.BotType.TAG);
    }

    @Test
    void fishDefendsWiderThanNitFacingMinRaise() {
        int fishContinues = countContinueFacingOpen("hearts_7", "diamonds_7", DpNpcEngine.BotType.FISH);
        int nitContinues = countContinueFacingOpen("hearts_7", "diamonds_7", DpNpcEngine.BotType.NIT);
        assertTrue(fishContinues > nitContinues,
                "FISH should defend 77 vs min-raise more often than NIT");
    }

    @Test
    void openRaiseSizesAreNotAllIdentical() {
        DpRoomBO room = buildUnopenedBtnRoom("hearts_A", "diamonds_K");
        int bb = room.getBigBlindChips();
        Set<Integer> amounts = new HashSet<>();
        boolean hasHalfBbStep = false;

        for (int i = 0; i < 120; i++) {
            DpRoomBO trialRoom = buildUnopenedBtnRoom("hearts_A", "diamonds_K");
            DpPlayer hero = trialRoom.getPlayers().get(0);
            if (i % 3 == 0) {
                hero.setChips(160);
            } else if (i % 3 == 1) {
                for (int j = 3; j < trialRoom.getPlayers().size(); j++) {
                    trialRoom.getPlayers().get(j).setFold(true);
                }
            }
            Random random = new Random(9000L + i);
            BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                    trialRoom,
                    hero,
                    0,
                    0.0,
                    0.48,
                    0.90,
                    0.22,
                    0.10,
                    random,
                    DpNpcEngine.BotType.MANIAC);
            if (action != null && action.getType() == BotActionType.RAISE) {
                amounts.add(action.getAmount());
                if (action.getAmount() % (bb / 2) == 0 && action.getAmount() % bb != 0) {
                    hasHalfBbStep = true;
                }
            }
        }

        assertTrue(amounts.size() >= 2, "open raise amounts should vary, got: " + amounts);
        assertTrue(hasHalfBbStep || amounts.contains(bb * 5 / 2),
                "expected at least one half-BB open size such as 2.5BB, got: " + amounts);
        assertTrue(amounts.stream().anyMatch(a -> a != 2 * bb),
                "not all opens should land on exactly 2BB");
    }

    private static void assertDefends(String card1, String card2, DpNpcEngine.BotType botType) {
        DpRoomBO room = buildFacingMinRaiseBtnRoom(card1, card2);
        Random random = new Random(42L);
        double vpip = botType == DpNpcEngine.BotType.FISH ? FISH_VPIP : TAG_VPIP;
        double pfr = botType == DpNpcEngine.BotType.FISH ? FISH_PFR : TAG_PFR;
        double callStation = botType == DpNpcEngine.BotType.FISH ? FISH_CALL_STATION : TAG_CALL_STATION;
        double fold = botType == DpNpcEngine.BotType.FISH ? FISH_FOLD : TAG_FOLD;

        int callAmount = room.getCurrentBetToCall() - room.getPlayers().get(0).getBet();
        BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                room.getPlayers().get(0),
                callAmount,
                0.02,
                vpip,
                pfr,
                callStation,
                fold,
                random,
                botType);

        assertNotEquals(BotActionType.FOLD, action.getType(),
                botType + " should defend " + card1 + card2 + " vs min-raise");
    }

    private static int countContinueFacingOpen(String card1, String card2, DpNpcEngine.BotType botType) {
        int continues = 0;
        for (int i = 0; i < 20; i++) {
            DpRoomBO room = buildFacingMinRaiseBtnRoom(card1, card2);
            Random random = new Random(3000L + i);
            double vpip;
            double pfr;
            double callStation;
            double fold;
            if (botType == DpNpcEngine.BotType.FISH) {
                vpip = FISH_VPIP;
                pfr = FISH_PFR;
                callStation = FISH_CALL_STATION;
                fold = FISH_FOLD;
            } else {
                vpip = 0.11;
                pfr = 0.15;
                callStation = 0.37;
                fold = 0.92;
            }
            int callAmount = room.getCurrentBetToCall() - room.getPlayers().get(0).getBet();
            BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                    room,
                    room.getPlayers().get(0),
                    callAmount,
                    0.02,
                    vpip,
                    pfr,
                    callStation,
                    fold,
                    random,
                    botType);
            if (action.getType() != BotActionType.FOLD) {
                continues++;
            }
        }
        return continues;
    }

    private static DpRoomBO buildFacingMinRaiseBtnRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildSixMaxTable();
        DpPlayer hero = room.getPlayers().get(0);
        hero.setHoleCards(List.of(toFullCard(heroCard1), toFullCard(heroCard2)));
        hero.setChips(5000);
        hero.setBet(0);

        DpPlayer opener = room.getPlayers().get(3);
        opener.setBet(room.getBigBlindChips() * 2);
        opener.setChips(4800);

        room.setRaiseLevel(1);
        room.setCurrentBetToCall(room.getBigBlindChips() * 2);
        return room;
    }

    private static DpRoomBO buildUnopenedBtnRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildSixMaxTable();
        DpPlayer hero = room.getPlayers().get(0);
        hero.setHoleCards(List.of(toFullCard(heroCard1), toFullCard(heroCard2)));
        hero.setChips(5000);
        hero.setBet(0);
        room.setRaiseLevel(0);
        room.setCurrentBetToCall(0);
        return room;
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

    /**
     * 6-max：seat0=BTN(dealer)，seat3=UTG opener；hero 在后位 lateFactor≈0.6。
     */
    private static DpRoomBO buildSixMaxTable() {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage("preflop");
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        room.setPot(15);

        for (int i = 0; i < 6; i++) {
            DpPlayer p = new DpPlayer();
            p.setNickname("P" + i);
            p.setChips(5000);
            p.setBet(0);
            p.setFold(false);
            p.setAllIn(false);
            p.setLeftThisHand(false);
            p.setBlind(0);
            if (i == 0) {
                p.setDealer(true);
            }
            if (i == 1) {
                p.setBlind(1);
                p.setBet(5);
            }
            if (i == 2) {
                p.setBlind(2);
                p.setBet(10);
            }
            room.getPlayers().add(p);
        }
        return room;
    }

}
