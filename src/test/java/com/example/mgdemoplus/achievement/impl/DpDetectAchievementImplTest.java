package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.room.support.DpSettlePersistJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DpDetectAchievementImplTest {

    private static final int WINNER_UID = 1001;
    private static final String WINNER_NICK = "hero";

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
        DpObservedHandRecordBO archived = new DpObservedHandRecordBO(
                "room-1",
                1L,
                0L,
                1L,
                10,
                20,
                "dealer",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                100,
                holes,
                net);
        return new DpSettlePersistJob("room-1", archived, room, List.of(), List.of());
    }

    private static DpRoomBO roomWithHuman(String nickname, int userId) {
        DpRoomBO room = new DpRoomBO();
        DpPlayer human = new DpPlayer();
        human.setNickname(nickname);
        human.setDpUserId(userId);
        room.setPlayers(List.of(human));
        return room;
    }
}
