package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService;
import com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTracePushService;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.rbac.DpPermissionService;
import com.example.mgdemoplus.room.support.DpRoomTestSupport;
import com.example.mgdemoplus.room.support.DpExperimentalDeckPresetPasswordGuard;
import com.example.mgdemoplus.room.support.DpSettlePersistenceDispatcher;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.utils.ResultUtil;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DpRbacExperimentalDeckPresetTest {

    private DpRoomServiceImpl svc;

    @BeforeEach
    void setUp() throws Exception {
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
                mock(com.example.mgdemoplus.moderation.DpSensitiveWordService.class),
                new DpExperimentalDeckPresetPasswordGuard("secret"),
                mock(DpNpcTagDecisionTracePushService.class),
                mock(DpPermissionService.class),
                DpRoomTestSupport.defaultInstanceProperties(),
                null);
        putRoom("room-1", "owner");
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    @Test
    void nonOwner_canVerifyPresetAndQueryStatus() {
        ResultUtil verify = svc.verifyExperimentalDeckPassword("room-1", "viewer", null);
        assertThat(verify.getSuccess()).isTrue();

        ResultUtil set = svc.setNextHandDeckPrefix("room-1", "viewer", List.of("hearts_A", "diamonds_K"), null);
        assertThat(set.getSuccess()).isTrue();
        assertThat(set.getData().get("presetCount")).isEqualTo(2);

        ResultUtil status = svc.getNextHandDeckPrefixStatus("room-1", "viewer", null);
        assertThat(status.getSuccess()).isTrue();
        assertThat(status.getData().get("presetCount")).isEqualTo(2);
    }

    @Test
    void missingRoom_returnsError() {
        ResultUtil result = svc.getNextHandDeckPrefixStatus("missing-room", "viewer", null);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getData().get("message")).isEqualTo("房间不存在");
    }

    private void putRoom(String roomId, String owner) throws Exception {
        DpRoomBO room = new DpRoomBO();
        room.setRoomId(roomId);
        room.setOwner(owner);
        DpRoomTestSupport.putRoom(svc, roomId, room);
    }
}
