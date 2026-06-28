package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.npc.mood.DpNpcMoodProperties;
import com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.room.support.DpRoomTestSupport;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.room.support.DpSettlePersistenceDispatcher;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link DpRoomServiceImpl#getAllRooms} 推送前为规则 bot 填充 {@code moodState}，真人/LLM 为 null。
 */
class DpRoomMoodStateSnapshotTest {

    private static final String ROOM_ID = "r-mood-snapshot";

    private DpRoomServiceImpl svc;
    private DpNpcMoodProperties moodProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        moodProperties = new DpNpcMoodProperties();
        moodProperties.setEnabled(true);
        moodProperties.setHighThreshold(0.35);
        moodProperties.setLowThreshold(-0.35);
        objectMapper = new ObjectMapper();
        svc = new DpRoomServiceImpl(
                DpRoomTestSupport.memoryRegistry(),
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
                objectMapper,
                mock(DpQuickMatchPushService.class),
                mock(DpFriendPresenceService.class),
                new RoomChatBuffer(),
                mock(DpRoomChatPersistenceService.class),
                mock(com.example.mgdemoplus.moderation.DpSensitiveWordService.class), null,
                mock(com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTracePushService.class),
                mock(com.example.mgdemoplus.rbac.DpPermissionService.class),
                DpRoomTestSupport.defaultInstanceProperties(),
                DpRoomTestSupport.joinableQuickMatchRoomIndex(),
                DpRoomTestSupport.mockQuickMatchWaitQueue(),
                DpRoomTestSupport.mockQuickMatchPairingLock(),
                DpRoomTestSupport.mockQuickMatchEventPublisher(),
                null);
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    private void seedRoom(DpPlayer... players) throws Exception {
        DpRoomBO room = new DpRoomBO();
        room.setRoomId(ROOM_ID);
        List<DpPlayer> list = new ArrayList<>();
        for (DpPlayer p : players) {
            list.add(p);
        }
        room.setPlayers(list);
        DpRoomTestSupport.putRoom(svc, ROOM_ID, room);
    }

    private static DpPlayer player(String nickname, double mood) {
        DpPlayer p = new DpPlayer();
        p.setNickname(nickname);
        p.setMood(mood);
        return p;
    }

    @Test
    @DisplayName("规则 bot mood≥highThreshold → moodState=HIGH")
    void ruleBotHighMoodGetsHighState() throws Exception {
        seedRoom(player("BOT_TAG_1", 0.4));
        DpRoomBO snapshot = svc.getAllRooms(ROOM_ID);
        assertThat(snapshot.getPlayers().get(0).getMoodState()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("规则 CUSTOM bot mood≤lowThreshold → moodState=LOW")
    void customRuleBotLowMoodGetsLowState() throws Exception {
        seedRoom(player("BOT_CUSTOM_3", -0.5));
        DpRoomBO snapshot = svc.getAllRooms(ROOM_ID);
        assertThat(snapshot.getPlayers().get(0).getMoodState()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("LLM bot 与真人 moodState 为 null")
    void llmAndHumanHaveNullMoodState() throws Exception {
        seedRoom(
                player("BOT_LLM_1", 0.0),
                player("alice", 0.0));
        DpRoomBO snapshot = svc.getAllRooms(ROOM_ID);
        assertNull(snapshot.getPlayers().get(0).getMoodState());
        assertNull(snapshot.getPlayers().get(1).getMoodState());
    }

    @Test
    @DisplayName("dp.npc.mood.enabled=false 时规则 bot 固定 NEUTRAL")
    void moodDisabledReturnsNeutral() throws Exception {
        moodProperties.setEnabled(false);
        seedRoom(player("BOT_TAG_2", 0.9));
        DpRoomBO snapshot = svc.getAllRooms(ROOM_ID);
        assertThat(snapshot.getPlayers().get(0).getMoodState()).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("JSON 序列化：规则 bot 含 moodState，LLM 省略")
    void jsonSerializationIncludesMoodStateForRuleBotOnly() throws Exception {
        seedRoom(player("BOT_TAG_5", 0.4), player("BOT_LLM_2", 0.0));
        DpRoomBO snapshot = svc.getAllRooms(ROOM_ID);
        String json = objectMapper.writeValueAsString(snapshot.getPlayers());
        JsonNode arr = objectMapper.readTree(json);
        assertThat(arr.get(0).get("moodState").asText()).isEqualTo("HIGH");
        assertFalse(arr.get(1).has("moodState"));
    }

    @Test
    @DisplayName("isRuleBotNickname 与 mood 填充口径一致")
    void ruleBotNicknameHelpersAlignWithMoodFill() {
        assertTrue(DpNpcEngine.isRuleBotNickname("BOT_TAG_1"));
        assertTrue(DpNpcEngine.isRuleBotNickname("BOT_CUSTOM_7"));
        assertFalse(DpNpcEngine.isRuleBotNickname("BOT_LLM_1"));
        assertFalse(DpNpcEngine.isRuleBotNickname("alice"));
    }
}
