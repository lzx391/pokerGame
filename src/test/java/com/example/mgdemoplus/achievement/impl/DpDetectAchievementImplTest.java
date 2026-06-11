package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.history.bo.DpObservedHandActionRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedSeatAtHandStartBO;
import com.example.mgdemoplus.history.bo.DpObservedStreetBoardBO;
import com.example.mgdemoplus.history.types.DpObservedHandActionType;
import com.example.mgdemoplus.room.support.DpSettlePersistJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DpDetectAchievementImplTest {

    private static final int WINNER_UID = 1001;
    private static final int VILLAIN_UID = 1002;
    private static final int THIRD_UID = 1003;
    private static final String WINNER_NICK = "hero";
    private static final String VILLAIN_NICK = "villain";
    private static final String THIRD_NICK = "third";
    private static final Long HAND_HISTORY_ID = 100L;

    private DpAchievementService achievementService;
    private DpDetectAchievementImpl detector;

    @BeforeEach
    void setUp() throws Exception {
        achievementService = mock(DpAchievementService.class);
        detector = new DpDetectAchievementImpl();
        Field field = DpDetectAchievementImpl.class.getDeclaredField("dpAchievementService");
        field.setAccessible(true);
        field.set(detector, achievementService);
    }

    @Test
    @DisplayName("27杂色赢家在6人桌解锁 twenty_seven_terminator")
    void unlocksWhenWinnerHasOffsuitTwoSevenOnSixMax() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "spades_7")),
                Map.of(WINNER_NICK, 120, VILLAIN_NICK, -120),
                seats(6, WINNER_NICK, VILLAIN_NICK),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("7-2 顺序无关")
    void orderIndependent() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("clubs_7", "diamonds_2")),
                Map.of(WINNER_NICK, 50),
                seats(6, WINNER_NICK),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("少于6人不解锁 twenty_seven_terminator")
    void skipsTwentySevenTerminatorBelowSixPlayers() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "spades_7")),
                Map.of(WINNER_NICK, 120, VILLAIN_NICK, -120),
                seats(2, WINNER_NICK, VILLAIN_NICK),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(eq(WINNER_UID),
                eq(DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR), any());
    }

    @Test
    @DisplayName("同花 27 不解锁")
    void skipsSuitedTwoSeven() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "hearts_7")),
                Map.of(WINNER_NICK, 50),
                seats(6, WINNER_NICK),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(eq(WINNER_UID),
                eq(DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR), any());
    }

    @Test
    @DisplayName("输家或未赢筹码不解锁")
    void skipsLoserOrNonPositiveNet() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "spades_7")),
                Map.of(WINNER_NICK, 0, VILLAIN_NICK, 0),
                seats(6, WINNER_NICK, VILLAIN_NICK),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(eq(WINNER_UID),
                eq(DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR), any());

        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "spades_7")),
                Map.of(WINNER_NICK, -80, VILLAIN_NICK, 80),
                seats(6, WINNER_NICK, VILLAIN_NICK),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(eq(WINNER_UID),
                eq(DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR), any());
    }

    @Test
    @DisplayName("皇家火箭击败较低火箭解锁 throne_usurper")
    void unlocksThroneUsurperWhenHigherStraightFlushWins() {
        List<String> board = List.of("spades_Q", "spades_J", "spades_10", "hearts_2", "clubs_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("spades_A", "spades_K"),
                        VILLAIN_NICK, List.of("spades_9", "spades_8")),
                Map.of(WINNER_NICK, 500, VILLAIN_NICK, -500),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_THRONE_USURPER,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("仅一方火箭不解锁 throne_usurper")
    void skipsThroneUsurperWithoutOpponentStraightFlush() {
        List<String> board = List.of("spades_Q", "spades_J", "spades_10", "hearts_2", "clubs_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("spades_A", "spades_K"),
                        VILLAIN_NICK, List.of("hearts_A", "diamonds_K")),
                Map.of(WINNER_NICK, 500, VILLAIN_NICK, -500),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("6人桌全员弃牌赢池解锁 table_clear")
    void unlocksTableClearWhenAllOpponentsFoldOnSixMax() {
        List<DpObservedHandActionRecordBO> actions = new ArrayList<>();
        for (int i = 1; i < 6; i++) {
            actions.add(action("preflop", "seat_" + i, DpObservedHandActionType.FOLD));
        }
        Map<String, List<String>> holes = new LinkedHashMap<>();
        holes.put(WINNER_NICK, List.of("hearts_A", "diamonds_K"));
        for (int i = 1; i < 6; i++) {
            holes.put("seat_" + i, List.of("clubs_7", "spades_2"));
        }
        Map<String, Integer> net = new LinkedHashMap<>();
        net.put(WINNER_NICK, 80);
        for (int i = 1; i < 6; i++) {
            net.put("seat_" + i, -16);
        }
        detector.detect(fullJob(
                holes,
                net,
                seats(6, WINNER_NICK),
                List.of(),
                actions,
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TABLE_CLEAR,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("少于6人不解锁 table_clear")
    void skipsTableClearBelowSixPlayers() {
        List<DpObservedHandActionRecordBO> actions = List.of(
                action("preflop", VILLAIN_NICK, DpObservedHandActionType.FOLD));
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_K"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2")),
                Map.of(WINNER_NICK, 80, VILLAIN_NICK, -80),
                seats(2, WINNER_NICK, VILLAIN_NICK),
                List.of(),
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("摊牌赢池不解锁 table_clear")
    void skipsTableClearOnShowdownWin() {
        List<String> board = List.of("hearts_2", "diamonds_3", "clubs_4", "spades_5", "hearts_6");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_A"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2")),
                Map.of(WINNER_NICK, 120, VILLAIN_NICK, -120),
                seats(6, WINNER_NICK, VILLAIN_NICK),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("花顺双抽河牌未中解锁 draw_insulator")
    void unlocksDrawInsulatorWhenComboDrawMisses() {
        List<String> board = List.of("hearts_9", "hearts_8", "clubs_2", "diamonds_5", "spades_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_J", "hearts_10")),
                Map.of(WINNER_NICK, -40, VILLAIN_NICK, 40),
                List.of(),
                board,
                List.of(),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_DRAW_INSULATOR,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("花顺双抽成同花不解锁 draw_insulator")
    void skipsDrawInsulatorWhenFlushCompletes() {
        List<String> board = List.of("hearts_9", "hearts_8", "clubs_2", "hearts_5", "spades_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_J", "hearts_10")),
                Map.of(WINNER_NICK, 60),
                List.of(),
                board,
                List.of(),
                roomWithHuman(WINNER_NICK, WINNER_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("转牌单张成顺、河牌无提升不解锁 natural_disaster")
    void skipsNaturalDisasterWhenTurnAloneOvertakes() {
        List<String> board = List.of("spades_K", "diamonds_9", "clubs_2", "hearts_5", "spades_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_K", "spades_4"),
                        VILLAIN_NICK, List.of("hearts_8", "diamonds_7")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("转河两张均参与成顺才解锁 natural_disaster（7-6/K92/85）")
    void unlocksNaturalDisasterWhenTurnAndRiverBothInStraight() {
        List<String> board = List.of("spades_K", "diamonds_9", "clubs_2", "hearts_8", "diamonds_5");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("diamonds_K", "hearts_9"),
                        VILLAIN_NICK, List.of("hearts_7", "hearts_6")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_NATURAL_DISASTER,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("翻牌成花领先被转河连追成葫芦解锁 natural_disaster（B73 flop795）")
    void unlocksNaturalDisasterWhenTurnRiverFullHouseComeback() {
        List<String> board = List.of("hearts_7", "hearts_9", "hearts_5", "diamonds_7", "clubs_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "hearts_2"),
                        VILLAIN_NICK, List.of("clubs_7", "diamonds_3")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_NATURAL_DISASTER,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("翻后已 4-to-flush 河牌单张成花不解锁 natural_disaster（hand 7605）")
    void skipsNaturalDisasterWhenFlopAlreadyFourToFlush() {
        List<String> board = List.of("diamonds_7", "diamonds_3", "spades_5", "clubs_2", "diamonds_K");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_5", "clubs_3"),
                        VILLAIN_NICK, List.of("diamonds_8", "diamonds_Q")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(),
                eq(DpAchievementService.CODE_NATURAL_DISASTER), any());
    }

    @Test
    @DisplayName("翻牌两对领先被转河连追成花解锁 natural_disaster")
    void unlocksNaturalDisasterWhenTurnRiverFlushComeback() {
        List<String> board = List.of("clubs_K", "hearts_9", "diamonds_2", "diamonds_5", "diamonds_A");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("spades_K", "hearts_9"),
                        VILLAIN_NICK, List.of("diamonds_6", "diamonds_7")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_NATURAL_DISASTER,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("翻牌未达两对不解锁 natural_disaster")
    void skipsNaturalDisasterWhenVictimNotTwoPairOnFlop() {
        List<String> board = List.of("hearts_K", "diamonds_9", "clubs_2", "hearts_8", "hearts_5");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("diamonds_A", "clubs_A"),
                        VILLAIN_NICK, List.of("hearts_7", "hearts_6")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(),
                eq(DpAchievementService.CODE_NATURAL_DISASTER), any());
    }

    @Test
    @DisplayName("底对抓诈解锁 soul_reader")
    void unlocksSoulReaderWithBottomPairCallDown() {
        List<String> board = List.of("spades_K", "hearts_9", "diamonds_5", "clubs_3", "hearts_2");
        List<DpObservedHandActionRecordBO> actions = List.of(
                action("river", VILLAIN_NICK, DpObservedHandActionType.BET));
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("clubs_J", "diamonds_2"),
                        VILLAIN_NICK, List.of("spades_A", "clubs_7")),
                Map.of(WINNER_NICK, 150, VILLAIN_NICK, -150),
                List.of(),
                board,
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_SOUL_READER,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("三人摊牌击败诈唬但输给第三人不算 soul_reader")
    void skipsSoulReaderWhenNotShowdownWinner() {
        List<String> board = List.of("spades_K", "hearts_9", "diamonds_5", "clubs_3", "hearts_2");
        List<DpObservedHandActionRecordBO> actions = List.of(
                action("river", VILLAIN_NICK, DpObservedHandActionType.BET));
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("clubs_J", "diamonds_2"),
                        VILLAIN_NICK, List.of("spades_A", "clubs_7"),
                        THIRD_NICK, List.of("diamonds_K", "clubs_K")),
                Map.of(WINNER_NICK, 50, VILLAIN_NICK, -150, THIRD_NICK, 100),
                List.of(),
                board,
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID, THIRD_NICK, THIRD_UID)),
                HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("强成牌赢池不解锁 soul_reader")
    void skipsSoulReaderWithStrongMadeHand() {
        List<String> board = List.of("spades_K", "hearts_9", "diamonds_5", "clubs_3", "hearts_2");
        List<DpObservedHandActionRecordBO> actions = List.of(
                action("river", VILLAIN_NICK, DpObservedHandActionType.BET));
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("clubs_K", "diamonds_K"),
                        VILLAIN_NICK, List.of("spades_A", "clubs_7")),
                Map.of(WINNER_NICK, 150, VILLAIN_NICK, -150),
                List.of(),
                board,
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("仅 1 名摊牌对手清零不解锁 sweep_all")
    void skipsSweepAllWhenOnlyOneShowdownOpponentBusted() {
        List<String> board = List.of("hearts_2", "diamonds_3", "clubs_4", "spades_5", "hearts_6");
        Map<String, Integer> chipsAtEnd = Map.of(WINNER_NICK, 600, VILLAIN_NICK, 0);
        detector.detect(sweepJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_A"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2")),
                Map.of(WINNER_NICK, 300, VILLAIN_NICK, -300),
                chipsAtEnd,
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(),
                eq(DpAchievementService.CODE_SWEEP_ALL), any());
    }


    @Test
    @DisplayName("5 名摊牌对手清零解锁 sweep_all")
    void unlocksSweepAllWhenFiveShowdownOpponentsBusted() {
        List<String> board = List.of("hearts_2", "diamonds_3", "clubs_4", "spades_5", "hearts_6");
        String[] oppNicks = {"opp1", "opp2", "opp3", "opp4", "opp5"};
        Map<String, List<String>> holes = new LinkedHashMap<>();
        Map<String, Integer> net = new LinkedHashMap<>();
        Map<String, Integer> chipsAtEnd = new LinkedHashMap<>();
        holes.put(WINNER_NICK, List.of("hearts_A", "diamonds_A"));
        net.put(WINNER_NICK, 500);
        chipsAtEnd.put(WINNER_NICK, 2500);
        for (String opp : oppNicks) {
            holes.put(opp, List.of("clubs_7", "spades_2"));
            net.put(opp, -100);
            chipsAtEnd.put(opp, 0);
        }
        detector.detect(sweepJob(holes, net, chipsAtEnd, List.of(), board, List.of(),
                roomWithPlayers(WINNER_NICK, WINNER_UID, oppNicks, new int[]{2001, 2002, 2003, 2004, 2005})),
                HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_SWEEP_ALL,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("对手仍有筹码不解锁 sweep_all")
    void skipsSweepAllWhenOpponentHasChipsLeft() {
        List<String> board = List.of("hearts_2", "diamonds_3", "clubs_4", "spades_5", "hearts_6");
        Map<String, Integer> chipsAtEnd = Map.of(WINNER_NICK, 400, VILLAIN_NICK, 50);
        detector.detect(sweepJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_A"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2")),
                Map.of(WINNER_NICK, 120, VILLAIN_NICK, -120),
                chipsAtEnd,
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("已弃牌者仍有筹码且摊牌对手不足 5 不解锁 sweep_all")
    void skipsSweepAllWhenFoldedPlayerStillHasChipsAndNotEnoughShowdownOpponents() {
        List<String> board = List.of("hearts_2", "diamonds_3", "clubs_4", "spades_5", "hearts_6");
        Map<String, Integer> chipsAtEnd = Map.of(
                WINNER_NICK, 600,
                VILLAIN_NICK, 0,
                THIRD_NICK, 200);
        List<DpObservedHandActionRecordBO> actions = List.of(
                action("preflop", THIRD_NICK, DpObservedHandActionType.FOLD));
        detector.detect(sweepJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_A"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2"),
                        THIRD_NICK, List.of("spades_Q", "diamonds_Q")),
                Map.of(WINNER_NICK, 300, VILLAIN_NICK, -300, THIRD_NICK, 0),
                chipsAtEnd,
                List.of(),
                board,
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID, THIRD_NICK, THIRD_UID)),
                HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(),
                eq(DpAchievementService.CODE_SWEEP_ALL), any());
    }

    @Test
    @DisplayName("翻后领先转牌不输河牌单张反超解锁 one_street_heaven")
    void unlocksOneStreetHeavenWhenRiverSingleCardOvertakes() {
        List<String> board = List.of("clubs_Q", "diamonds_5", "hearts_2", "clubs_4", "spades_6");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_Q", "diamonds_J"),
                        VILLAIN_NICK, List.of("spades_8", "spades_7")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_ONE_STREET_HEAVEN,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("转牌已反超不解锁 one_street_heaven")
    void skipsOneStreetHeavenWhenOvertakenOnTurn() {
        List<String> board = List.of("spades_K", "clubs_7", "diamonds_6", "hearts_5", "clubs_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_K", "diamonds_K"),
                        VILLAIN_NICK, List.of("spades_9", "spades_8")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(),
                eq(DpAchievementService.CODE_ONE_STREET_HEAVEN), any());
    }

    @Test
    @DisplayName("转牌落后终局最强赢池解锁 final_oracle")
    void unlocksFinalOracleWhenBehindOnTurnButWinsShowdown() {
        List<String> board = List.of("hearts_K", "diamonds_Q", "clubs_J", "hearts_9", "hearts_10");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_2"),
                        VILLAIN_NICK, List.of("spades_K", "spades_Q")),
                Map.of(WINNER_NICK, 200, VILLAIN_NICK, -200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_FINAL_ORACLE,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("转牌已领先不解锁 final_oracle")
    void skipsFinalOracleWhenAlreadyAheadOnTurn() {
        List<String> board = List.of("spades_A", "clubs_7", "diamonds_2", "hearts_5", "clubs_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_K"),
                        VILLAIN_NICK, List.of("spades_Q", "spades_J")),
                Map.of(WINNER_NICK, 200, VILLAIN_NICK, -200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(),
                eq(DpAchievementService.CODE_FINAL_ORACLE), any());
    }

    @Test
    @DisplayName("转牌同型杂色底牌河牌成同花解锁 mirror_duel")
    void unlocksMirrorDuelWhenTurnRankMatchesAndRiverFlushWins() {
        List<String> board = List.of("hearts_K", "hearts_Q", "clubs_J", "hearts_9", "hearts_2");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_3"),
                        VILLAIN_NICK, List.of("clubs_A", "spades_5")),
                Map.of(WINNER_NICK, 200, VILLAIN_NICK, -200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_MIRROR_DUEL,
                HAND_HISTORY_ID);
    }

    @Test
    @DisplayName("同花底牌不解锁 mirror_duel")
    void skipsMirrorDuelWhenWinnerHoleIsSuited() {
        List<String> board = List.of("hearts_K", "diamonds_Q", "clubs_J", "hearts_9", "hearts_2");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "hearts_3"),
                        VILLAIN_NICK, List.of("clubs_A", "spades_5")),
                Map.of(WINNER_NICK, 200, VILLAIN_NICK, -200),
                List.of(),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(anyInt(),
                eq(DpAchievementService.CODE_MIRROR_DUEL), any());
    }

    @Test
    @DisplayName("BOT 赢家不写入成就")
    void skipsBotWinner() {
        DpRoomBO room = new DpRoomBO();
        DpPlayer bot = new DpPlayer();
        bot.setNickname("BOT_FISH_1");
        bot.setDpUserId(999);
        room.setPlayers(List.of(bot));

        detector.detect(job(
                Map.of("BOT_FISH_1", List.of("hearts_2", "spades_7")),
                Map.of("BOT_FISH_1", 200),
                seats(6, "BOT_FISH_1"),
                room), HAND_HISTORY_ID);

        verify(achievementService, never()).unlockIfAbsent(eq(999),
                eq(DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR), any());
    }

    private static DpSettlePersistJob job(Map<String, List<String>> holes,
                                          Map<String, Integer> net,
                                          List<DpObservedSeatAtHandStartBO> seats,
                                          DpRoomBO room) {
        return fullJob(holes, net, seats, Map.of(), List.of(), List.of(), room);
    }

    private static DpSettlePersistJob sweepJob(Map<String, List<String>> holes,
                                               Map<String, Integer> net,
                                               Map<String, Integer> chipsAtEnd,
                                               List<DpObservedSeatAtHandStartBO> seats,
                                               List<String> board,
                                               List<DpObservedHandActionRecordBO> actions,
                                               DpRoomBO room) {
        return fullJob(holes, net, seats, chipsAtEnd, board, actions, room);
    }

    private static DpSettlePersistJob fullJob(Map<String, List<String>> holes,
                                              Map<String, Integer> net,
                                              List<DpObservedSeatAtHandStartBO> seats,
                                              List<String> board,
                                              List<DpObservedHandActionRecordBO> actions,
                                              DpRoomBO room) {
        return fullJob(holes, net, seats, Map.of(), board, actions, room);
    }

    private static DpSettlePersistJob fullJob(Map<String, List<String>> holes,
                                              Map<String, Integer> net,
                                              List<DpObservedSeatAtHandStartBO> seats,
                                              Map<String, Integer> chipsAtEnd,
                                              List<String> board,
                                              List<DpObservedHandActionRecordBO> actions,
                                              DpRoomBO room) {
        List<DpObservedStreetBoardBO> boards = new ArrayList<>();
        if (board.size() >= 3) {
            boards.add(new DpObservedStreetBoardBO("flop", board.subList(0, 3)));
        }
        if (board.size() >= 4) {
            boards.add(new DpObservedStreetBoardBO("turn", board.subList(0, 4)));
        }
        if (board.size() >= 5) {
            boards.add(new DpObservedStreetBoardBO("river", board));
        }
        DpObservedHandRecordBO archived = new DpObservedHandRecordBO(
                "room-1",
                1L,
                0L,
                1L,
                10,
                20,
                0,
                "dealer",
                seats,
                boards,
                actions,
                List.of(),
                100,
                holes,
                net,
                chipsAtEnd);
        return new DpSettlePersistJob("room-1", archived, room, List.of(), List.of());
    }

    private static List<DpObservedSeatAtHandStartBO> seats(int count, String... named) {
        List<DpObservedSeatAtHandStartBO> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String nick = i < named.length ? named[i] : "seat_" + i;
            list.add(new DpObservedSeatAtHandStartBO(i, nick, 0, 1000));
        }
        return list;
    }

    private static DpObservedHandActionRecordBO action(String stage,
                                                       String actor,
                                                       DpObservedHandActionType type) {
        return new DpObservedHandActionRecordBO(0L, stage, actor, type, 0, 0, 0, 0, 0);
    }

    private static DpRoomBO roomWithHuman(String nickname, int userId) {
        DpRoomBO room = new DpRoomBO();
        DpPlayer human = new DpPlayer();
        human.setNickname(nickname);
        human.setDpUserId(userId);
        room.setPlayers(List.of(human));
        return room;
    }

    private static DpRoomBO roomWithHumans(String nick1, int uid1, String nick2, int uid2) {
        return roomWithHumans(nick1, uid1, nick2, uid2, null, 0);
    }

    private static DpRoomBO roomWithHumans(String nick1, int uid1, String nick2, int uid2,
                                           String nick3, int uid3) {
        DpRoomBO room = new DpRoomBO();
        List<DpPlayer> players = new ArrayList<>();
        players.add(player(nick1, uid1));
        players.add(player(nick2, uid2));
        if (nick3 != null) {
            players.add(player(nick3, uid3));
        }
        room.setPlayers(players);
        return room;
    }

    private static DpPlayer player(String nickname, int userId) {
        DpPlayer p = new DpPlayer();
        p.setNickname(nickname);
        p.setDpUserId(userId);
        return p;
    }

    private static DpRoomBO roomWithPlayers(String heroNick, int heroUid, String[] oppNicks, int[] oppUids) {
        DpRoomBO room = new DpRoomBO();
        List<DpPlayer> players = new ArrayList<>();
        players.add(player(heroNick, heroUid));
        for (int i = 0; i < oppNicks.length; i++) {
            players.add(player(oppNicks[i], oppUids[i]));
        }
        room.setPlayers(players);
        return room;
    }
}
