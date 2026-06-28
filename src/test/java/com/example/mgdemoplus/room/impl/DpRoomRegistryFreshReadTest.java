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
import com.example.mgdemoplus.room.support.DpRoomRegistry;
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Regression: service must read live registry state after {@link DpRoomServiceImpl#createRoom},
 * not a construction-time {@code roomMap()} snapshot (multi-instance Redis migration).
 */
class DpRoomRegistryFreshReadTest {

    private DpRoomServiceImpl svc;
    private StaleSnapshotTrapRegistry registry;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        registry = new StaleSnapshotTrapRegistry();
        svc = new DpRoomServiceImpl(
                registry,
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

    @Test
    void createRoomThenStartGameAndSnapshotUsesLiveRegistryNotFrozenRoomMap() {
        DpRoomBO created = svc.createRoom("owner", null, 1, 2, 50, "pw", 6, 30);
        assertNotNull(created);
        String roomId = created.getRoomId();

        for (DpPlayer p : created.getPlayers()) {
            p.setReady(true);
        }
        registry.put(roomId, created);

        assertTrue(svc.startGame(roomId, "owner"), "startGame must see room via registry.get, not stale snapshot");

        DpRoomBO snapshot = svc.getAllRooms(roomId);
        assertNotNull(snapshot, "WS snapshot path must resolve room after create on another logical instance");
        assertTrue(snapshot.isPlaying());
    }

    /**
     * Simulates the broken pattern: {@link #roomMap()} frozen empty at construction while
     * {@link #get(String)} reads the live backing store.
     */
    static final class StaleSnapshotTrapRegistry implements DpRoomRegistry {

        private final Map<String, DpRoomBO> live = new HashMap<>();
        private final Map<String, DpRoomBO> frozenEmptySnapshot = Collections.emptyMap();

        @Override
        public Map<String, DpRoomBO> roomMap() {
            return frozenEmptySnapshot;
        }

        @Override
        public DpRoomBO get(String roomId) {
            return live.get(roomId);
        }

        @Override
        public void put(String roomId, DpRoomBO room) {
            live.put(roomId, room);
        }

        @Override
        public Collection<DpRoomBO> values() {
            return live.values();
        }

        @Override
        public Set<String> roomIds() {
            return Set.copyOf(live.keySet());
        }

        @Override
        public boolean contains(String roomId) {
            return live.containsKey(roomId);
        }

        @Override
        public boolean tryUnregisterEmptyRoomAssumeLocked(
                DpRoomBO r, String roomId, int liveHumanTableCount, int spectatorCount) {
            if (r == null || liveHumanTableCount > 0 || spectatorCount > 0) {
                return false;
            }
            return live.remove(roomId, r);
        }

        @Override
        public <T> T runExclusive(String roomId, Function<DpRoomBO, T> action) {
            DpRoomBO r = live.get(roomId);
            if (r == null) {
                return null;
            }
            synchronized (r) {
                return action.apply(r);
            }
        }

        @Override
        public boolean isRedisBacked() {
            return false;
        }

        @Override
        public void saveAfterMutation(String roomId, DpRoomBO room, String reason) {
            live.put(roomId, room);
        }

        @Override
        public long revision(String roomId) {
            return 0L;
        }
    }
}
