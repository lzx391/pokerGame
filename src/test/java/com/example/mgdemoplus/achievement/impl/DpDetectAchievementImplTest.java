package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.history.bo.DpObservedHandActionRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedStreetBoardBO;
import com.example.mgdemoplus.history.types.DpObservedHandActionType;
import com.example.mgdemoplus.room.support.DpSettlePersistJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DpDetectAchievementImplTest {

    private static final int WINNER_UID = 1001;
    private static final int VILLAIN_UID = 1002;
    private static final String WINNER_NICK = "hero";
    private static final String VILLAIN_NICK = "villain";

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
    @DisplayName("27杂色赢家解锁 twenty_seven_terminator")
    void unlocksWhenWinnerHasOffsuitTwoSeven() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "spades_7")),
                Map.of(WINNER_NICK, 120, "villain", -120),
                roomWithHuman(WINNER_NICK, WINNER_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR);
    }

    @Test
    @DisplayName("7-2 顺序无关")
    void orderIndependent() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("clubs_7", "diamonds_2")),
                Map.of(WINNER_NICK, 50),
                roomWithHuman(WINNER_NICK, WINNER_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR);
    }

    @Test
    @DisplayName("同花 27 不解锁")
    void skipsSuitedTwoSeven() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "hearts_7")),
                Map.of(WINNER_NICK, 50),
                roomWithHuman(WINNER_NICK, WINNER_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR);
    }

    @Test
    @DisplayName("输家或未赢筹码不解锁")
    void skipsLoserOrNonPositiveNet() {
        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "spades_7")),
                Map.of(WINNER_NICK, 0, "villain", 0),
                roomWithHuman(WINNER_NICK, WINNER_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR);

        detector.detect(job(
                Map.of(WINNER_NICK, List.of("hearts_2", "spades_7")),
                Map.of(WINNER_NICK, -80, "villain", 80),
                roomWithHuman(WINNER_NICK, WINNER_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR);
    }

    @Test
    @DisplayName("皇家同花顺击败较低同花顺解锁 throne_usurper")
    void unlocksThroneUsurperWhenHigherStraightFlushWins() {
        List<String> board = List.of("spades_Q", "spades_J", "spades_10", "hearts_2", "clubs_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("spades_A", "spades_K"),
                        VILLAIN_NICK, List.of("spades_9", "spades_8")),
                Map.of(WINNER_NICK, 500, VILLAIN_NICK, -500),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_THRONE_USURPER);
    }

    @Test
    @DisplayName("仅一方同花顺不解锁 throne_usurper")
    void skipsThroneUsurperWithoutOpponentStraightFlush() {
        List<String> board = List.of("spades_Q", "spades_J", "spades_10", "hearts_2", "clubs_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("spades_A", "spades_K"),
                        VILLAIN_NICK, List.of("hearts_A", "diamonds_K")),
                Map.of(WINNER_NICK, 500, VILLAIN_NICK, -500),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_THRONE_USURPER);
    }

    @Test
    @DisplayName("全员弃牌赢池解锁 table_clear")
    void unlocksTableClearWhenAllOpponentsFold() {
        List<DpObservedHandActionRecordBO> actions = List.of(
                action("preflop", VILLAIN_NICK, DpObservedHandActionType.FOLD));
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_K"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2")),
                Map.of(WINNER_NICK, 80, VILLAIN_NICK, -80),
                List.of(),
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TABLE_CLEAR);
    }

    @Test
    @DisplayName("摊牌赢池不解锁 table_clear")
    void skipsTableClearOnShowdownWin() {
        List<String> board = List.of("hearts_2", "diamonds_3", "clubs_4", "spades_5", "hearts_6");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_A"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2")),
                Map.of(WINNER_NICK, 120, VILLAIN_NICK, -120),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_TABLE_CLEAR);
    }

    @Test
    @DisplayName("花顺双抽河牌未中解锁 draw_insulator")
    void unlocksDrawInsulatorWhenComboDrawMisses() {
        List<String> board = List.of("hearts_9", "hearts_8", "clubs_2", "diamonds_5", "spades_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_J", "hearts_10")),
                Map.of(WINNER_NICK, -40, VILLAIN_NICK, 40),
                board,
                List.of(),
                roomWithHuman(WINNER_NICK, WINNER_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_DRAW_INSULATOR);
    }

    @Test
    @DisplayName("花顺双抽成同花不解锁 draw_insulator")
    void skipsDrawInsulatorWhenFlushCompletes() {
        List<String> board = List.of("hearts_9", "hearts_8", "clubs_2", "hearts_5", "spades_3");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("hearts_J", "hearts_10")),
                Map.of(WINNER_NICK, 60),
                board,
                List.of(),
                roomWithHuman(WINNER_NICK, WINNER_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_DRAW_INSULATOR);
    }

    @Test
    @DisplayName("翻牌领先被转河反超解锁 natural_disaster")
    void unlocksNaturalDisasterWhenTurnRiverComeback() {
        List<String> board = List.of("hearts_K", "diamonds_9", "clubs_2", "hearts_8", "hearts_5");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("diamonds_A", "clubs_A"),
                        VILLAIN_NICK, List.of("hearts_7", "hearts_6")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_NATURAL_DISASTER);
    }

    @Test
    @DisplayName("翻牌未领先不解锁 natural_disaster")
    void skipsNaturalDisasterWhenNotLeadingOnFlop() {
        List<String> board = List.of("diamonds_7", "clubs_6", "spades_2", "hearts_8", "hearts_5");
        detector.detect(fullJob(
                Map.of(WINNER_NICK, List.of("diamonds_A", "clubs_A"),
                        VILLAIN_NICK, List.of("hearts_7", "hearts_6")),
                Map.of(WINNER_NICK, -200, VILLAIN_NICK, 200),
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_NATURAL_DISASTER);
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
                board,
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_SOUL_READER);
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
                board,
                actions,
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_SOUL_READER);
    }

    @Test
    @DisplayName("摊牌清零对手解锁 sweep_all")
    void unlocksSweepAllWhenShowdownOpponentsBusted() {
        List<String> board = List.of("hearts_2", "diamonds_3", "clubs_4", "spades_5", "hearts_6");
        Map<String, Integer> chipsAtEnd = Map.of(WINNER_NICK, 600, VILLAIN_NICK, 0);
        detector.detect(sweepJob(
                Map.of(WINNER_NICK, List.of("hearts_A", "diamonds_A"),
                        VILLAIN_NICK, List.of("clubs_7", "spades_2")),
                Map.of(WINNER_NICK, 300, VILLAIN_NICK, -300),
                chipsAtEnd,
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_SWEEP_ALL);
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
                board,
                List.of(),
                roomWithHumans(WINNER_NICK, WINNER_UID, VILLAIN_NICK, VILLAIN_UID)));

        verify(achievementService, never()).unlockIfAbsent(WINNER_UID, DpAchievementService.CODE_SWEEP_ALL);
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
                room));

        verify(achievementService, never()).unlockIfAbsent(999, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR);
    }

    private static DpSettlePersistJob job(Map<String, List<String>> holes,
                                          Map<String, Integer> net,
                                          DpRoomBO room) {
        return fullJob(holes, net, Map.of(), List.of(), List.of(), room);
    }

    private static DpSettlePersistJob sweepJob(Map<String, List<String>> holes,
                                               Map<String, Integer> net,
                                               Map<String, Integer> chipsAtEnd,
                                               List<String> board,
                                               List<DpObservedHandActionRecordBO> actions,
                                               DpRoomBO room) {
        return fullJob(holes, net, chipsAtEnd, board, actions, room);
    }

    private static DpSettlePersistJob fullJob(Map<String, List<String>> holes,
                                              Map<String, Integer> net,
                                              List<String> board,
                                              List<DpObservedHandActionRecordBO> actions,
                                              DpRoomBO room) {
        return fullJob(holes, net, Map.of(), board, actions, room);
    }

    private static DpSettlePersistJob fullJob(Map<String, List<String>> holes,
                                              Map<String, Integer> net,
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
                List.of(),
                boards,
                actions,
                List.of(),
                100,
                holes,
                net,
                chipsAtEnd);
        return new DpSettlePersistJob("room-1", archived, room, List.of(), List.of());
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
        DpRoomBO room = new DpRoomBO();
        DpPlayer p1 = new DpPlayer();
        p1.setNickname(nick1);
        p1.setDpUserId(uid1);
        DpPlayer p2 = new DpPlayer();
        p2.setNickname(nick2);
        p2.setDpUserId(uid2);
        room.setPlayers(List.of(p1, p2));
        return room;
    }
}
