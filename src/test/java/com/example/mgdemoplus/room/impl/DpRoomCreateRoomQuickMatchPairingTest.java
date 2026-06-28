package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.config.DpInstanceProperties;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.quickmatch.DpQuickMatchPairingLock;
import com.example.mgdemoplus.quickmatch.DpQuickMatchWaitQueue;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.quickmatch.notify.QuickMatchEventPublisher;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry;
import com.example.mgdemoplus.room.support.DpRoomTestSupport;
import com.example.mgdemoplus.room.support.DpSettlePersistenceDispatcher;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: public {@link DpRoomServiceImpl#createRoom} must drain Redis wait queue into the new joinable room.
 */
class DpRoomCreateRoomQuickMatchPairingTest {

    private DpQuickMatchWaitQueue waitQueue;
    private DpQuickMatchPairingLock pairingLock;
    private JoinableQuickMatchRoomIndex joinableIndex;
    private DpRoomServiceImpl svc;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        waitQueue = mock(DpQuickMatchWaitQueue.class);
        pairingLock = mock(DpQuickMatchPairingLock.class);
        joinableIndex = DpRoomTestSupport.joinableQuickMatchRoomIndex();

        DpInstanceProperties props = DpRoomTestSupport.defaultInstanceProperties();

        when(pairingLock.tryAcquire()).thenReturn("test-lock-token");
        when(pairingLock.acquireWithRetry()).thenReturn("test-lock-token");
        when(waitQueue.pruneWhileLocked(anyLong(), anyLong(), any(Predicate.class))).thenReturn(List.of());
        when(waitQueue.snapshotWhileLocked()).thenReturn(List.of());
        when(waitQueue.sizeWhileLocked()).thenReturn(1, 0, 0);
        when(waitQueue.pollHeadWhileLocked(anyInt()))
                .thenReturn(List.of(new DpQuickMatchWaitEntry("alice", 1, System.currentTimeMillis())))
                .thenReturn(List.of());

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
                null,
                mock(com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTracePushService.class),
                mock(com.example.mgdemoplus.rbac.DpPermissionService.class),
                props,
                joinableIndex,
                waitQueue,
                pairingLock,
                mock(QuickMatchEventPublisher.class),
                null);
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    @Test
    void createRoom_publicRoom_drainsWaitQueue() throws Exception {
        DpRoomBO created = svc.createRoom("bob", 2, 5, 10, 50, null, 6, 30);
        assertTrue(joinableIndex.indexedRoomCount() >= 1 || created != null);

        verify(pairingLock, atLeastOnce()).acquireWithRetry();
        verify(waitQueue, atLeastOnce()).pollHeadWhileLocked(anyInt());

        DpRoomBO live = DpRoomTestSupport.getRoom(svc, created.getRoomId());
        assertTrue(live.getPlayers().stream().anyMatch(p -> p != null && "alice".equals(p.getNickname())),
                "createRoom pairing should join queued waiter alice into bob's new public room");
    }
}
