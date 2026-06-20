package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpPot;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.npc.mood.DpNpcMoodProperties;
import com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.room.support.DpSettlePersistenceDispatcher;
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
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

/**
 * 规则 bot 结算 mood：本手净输且筹码低于进房初始时应扣 mood。
 */
class DpRoomMoodAfterHandTest {

    private DpRoomServiceImpl svc;
    private DpNpcMoodProperties moodProperties;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        moodProperties = new DpNpcMoodProperties();
        moodProperties.setEnabled(true);
        moodProperties.setDeltaWin(0.2);
        moodProperties.setDeltaLose(-0.2);
        svc = new DpRoomServiceImpl(
                mock(DpHandHistoryPersistService.class),
                mock(DpSettlePersistenceDispatcher.class),
                mock(DpLlmNpcDecisionService.class),
                mock(DpNpcTableTalkService.class),
                moodProperties,
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
                mock(com.example.mgdemoplus.rbac.DpPermissionService.class));
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    private void invokeAutoSettleNormalPotShowdownPath(DpRoomBO room) throws Exception {
        Method m = DpRoomServiceImpl.class.getDeclaredMethod(
                "autoSettleNormalPotShowdownPath", DpRoomBO.class, boolean.class);
        m.setAccessible(true);
        m.invoke(svc, room, false);
    }

    private static DpRoomBO roomWithLosingRuleBot() {
        DpRoomBO r = new DpRoomBO();
        r.setRoomId("r-mood-after-hand");
        r.setStartingChips(500);
        r.setBigBlindChips(10);
        r.setCurrentStage("preflop");
        r.setPlayerStatsMap(new HashMap<>());

        // 规则 bot 本手已下注 20，筹码扣至 480 后弃牌
        DpPlayer bot = new DpPlayer();
        bot.setNickname("BOT_TAG_1");
        bot.setChips(480);
        bot.setTotalBet(20);
        bot.setFold(true);
        bot.setMood(0.0);

        // 对手收池：分池前同样 480，结算后 +40
        DpPlayer winner = new DpPlayer();
        winner.setNickname("alice");
        winner.setChips(480);
        winner.setTotalBet(20);
        winner.setFold(false);

        List<DpPlayer> players = new ArrayList<>();
        players.add(bot);
        players.add(winner);
        r.setPlayers(players);

        DpPot pot = new DpPot();
        pot.setAmount(40);
        pot.setEligiblePlayers(List.of("alice"));
        r.setPots(new ArrayList<>(List.of(pot)));
        r.setPot(40);
        return r;
    }

    @Test
    @DisplayName("规则 bot 本手净输且筹码低于初始带入 → mood 扣 deltaLose")
    void ruleBotLosesHandBelowStartingChipsDecreasesMood() throws Exception {
        DpRoomBO r = roomWithLosingRuleBot();
        DpPlayer bot = r.getPlayers().get(0);
        assertThat(com.example.mgdemoplus.npc.engine.DpNpcEngine.isRuleBotPlayer(bot)).isTrue();
        invokeAutoSettleNormalPotShowdownPath(r);
        assertThat(r.getCurrentStage()).isEqualTo("settled");
        assertThat(bot.getChips()).isEqualTo(480);
        assertThat(bot.getMood()).isCloseTo(-0.2, within(0.001));
    }

    @Test
    @DisplayName("规则 bot 本手净输但筹码仍≥初始带入 → mood 不变")
    void ruleBotLosesHandButStillAboveStartingChipsKeepsMood() throws Exception {
        DpRoomBO r = roomWithLosingRuleBot();
        DpPlayer bot = r.getPlayers().get(0);
        bot.setChips(620);
        bot.setTotalBet(20);
        DpPlayer winner = r.getPlayers().get(1);
        winner.setChips(620);
        winner.setTotalBet(20);
        invokeAutoSettleNormalPotShowdownPath(r);
        assertThat(bot.getMood()).isZero();
    }

    @Test
    @DisplayName("规则 bot 本手净赢 → mood 加 deltaWin")
    void ruleBotWinsHandIncreasesMood() throws Exception {
        DpRoomBO r = roomWithLosingRuleBot();
        DpPlayer bot = r.getPlayers().get(0);
        DpPlayer human = r.getPlayers().get(1);
        bot.setFold(false);
        human.setFold(false);
        bot.setHoleCards(List.of("spades_A", "hearts_A"));
        human.setHoleCards(List.of("spades_2", "hearts_3"));
        r.setCommunityCards(List.of("diamonds_K", "clubs_K", "hearts_K", "spades_4", "clubs_5"));
        r.setCurrentStage("showdown");
        r.getPots().get(0).setEligiblePlayers(List.of("BOT_TAG_1", "alice"));
        invokeAutoSettleNormalPotShowdownPath(r);
        assertThat(bot.getChips()).isEqualTo(520);
        assertThat(bot.getMood()).isCloseTo(0.2, within(0.001));
    }

    @Test
    @DisplayName("旧 handDelta 口径在分池前快照下恒为 0，需减 totalBet")
    void handDeltaMustSubtractTotalBetAtPrePotSnapshot() {
        int chipsBefore = 480;
        int chipsAfter = 480;
        int totalBet = 20;
        assertThat(chipsAfter - chipsBefore).isZero();
        assertThat(chipsAfter - chipsBefore - totalBet).isEqualTo(-20);
    }
}
