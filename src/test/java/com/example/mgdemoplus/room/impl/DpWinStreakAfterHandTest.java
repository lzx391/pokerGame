package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.room.support.DpMaxWinStreakFlush;
import com.example.mgdemoplus.room.support.DpSettlePersistenceDispatcher;
import com.example.mgdemoplus.room.support.DpRoomTestSupport;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link DpRoomServiceImpl#applyWinStreakAfterHand}：断连胜时返回异步 flush 条目，内存 map 清零。
 */
class DpWinStreakAfterHandTest {

    private static final int LOSER_UID = 501;
    private static final String LOSER_NICK = "alice";
    private static final String WINNER_NICK = "bob";

    private DpRoomServiceImpl svc;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        svc = new DpRoomServiceImpl(
                DpRoomTestSupport.memoryRegistry(),
                mock(DpHandHistoryPersistService.class),
                mock(DpSettlePersistenceDispatcher.class),
                mock(DpLlmNpcDecisionService.class),
                mock(DpNpcTableTalkService.class),
                mock(com.example.mgdemoplus.npc.mood.DpNpcMoodProperties.class),
                mock(DpGameRoomPushService.class),
                mock(DpUserMapper.class),
                mock(DpUserStatsMapper.class),
                mock(DpLeaderboardWeeklyWriteService.class),
                mock(DpHandHistoryObservedService.class),
                new LlmNpcGlobalHandConversationStore(),
                mock(DpRoomHallService.class),
                new ObjectMapper(),
                mock(DpQuickMatchPushService.class),
                mock(DpFriendPresenceService.class),
                new RoomChatBuffer(),
                mock(DpRoomChatPersistenceService.class),
                mock(com.example.mgdemoplus.moderation.DpSensitiveWordService.class), null,
                mock(com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTracePushService.class),
                mock(com.example.mgdemoplus.rbac.DpPermissionService.class),
                DpRoomTestSupport.defaultInstanceProperties(),
                null);
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    @SuppressWarnings("unchecked")
    private List<DpMaxWinStreakFlush> invokeApplyWinStreakAfterHand(DpRoomBO room, Set<String> winners)
            throws Exception {
        Method m = DpRoomServiceImpl.class.getDeclaredMethod(
                "applyWinStreakAfterHand", DpRoomBO.class, Set.class);
        m.setAccessible(true);
        return (List<DpMaxWinStreakFlush>) m.invoke(svc, room, winners);
    }

    private static DpRoomBO roomWithHumanPlayers() {
        DpRoomBO r = new DpRoomBO();
        r.setRoomId("r-streak-test");

        DpPlayer loser = new DpPlayer();
        loser.setNickname(LOSER_NICK);
        loser.setDpUserId(LOSER_UID);

        DpPlayer winner = new DpPlayer();
        winner.setNickname(WINNER_NICK);
        winner.setDpUserId(502);

        List<DpPlayer> players = new ArrayList<>();
        players.add(loser);
        players.add(winner);
        r.setPlayers(players);
        return r;
    }

    @Test
    @DisplayName("streak 5 → lose → flush userId+5 and map becomes 0")
    void breakingStreakFlushesPeakBeforeClearingMap() throws Exception {
        DpRoomBO r = roomWithHumanPlayers();
        r.getWinStreakByNickname().put(LOSER_NICK, 5);

        List<DpMaxWinStreakFlush> flushes =
                invokeApplyWinStreakAfterHand(r, Set.of(WINNER_NICK));

        assertThat(flushes).containsExactly(new DpMaxWinStreakFlush(LOSER_UID, 5));
        assertThat(r.getWinStreakByNickname().get(LOSER_NICK)).isZero();
        assertThat(r.getWinStreakByNickname().get(WINNER_NICK)).isEqualTo(1);
    }

    @Test
    @DisplayName("loser with streak 0 produces no flush entry")
    void zeroStreakLoserDoesNotFlush() throws Exception {
        DpRoomBO r = roomWithHumanPlayers();
        r.getWinStreakByNickname().put(LOSER_NICK, 0);

        List<DpMaxWinStreakFlush> flushes =
                invokeApplyWinStreakAfterHand(r, Set.of(WINNER_NICK));

        assertThat(flushes).isEmpty();
        assertThat(r.getWinStreakByNickname().get(LOSER_NICK)).isZero();
    }
}
