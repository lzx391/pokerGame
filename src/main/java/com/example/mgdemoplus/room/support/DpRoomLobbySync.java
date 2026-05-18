package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;

/**
 * Quick-match joinable index maintenance and {@code dp_room_lobby} summary sync after room mutations.
 */
public final class DpRoomLobbySync {

    private final DpRoomRegistry registry;
    private final JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex;
    private final DpRoomHallService dpRoomHallService;
    private final DpGameRoomPushService gameRoomPushService;
    private final DpRoomChatPersistenceService roomChatPersistenceService;

    public DpRoomLobbySync(
            DpRoomRegistry registry,
            JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex,
            DpRoomHallService dpRoomHallService,
            DpGameRoomPushService gameRoomPushService,
            DpRoomChatPersistenceService roomChatPersistenceService) {
        this.registry = registry;
        this.joinableQuickMatchRoomIndex = joinableQuickMatchRoomIndex;
        this.dpRoomHallService = dpRoomHallService;
        this.gameRoomPushService = gameRoomPushService;
        this.roomChatPersistenceService = roomChatPersistenceService;
    }

    public JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex() {
        return joinableQuickMatchRoomIndex;
    }

    public void rebuildJoinableQuickMatchIndex(long nowMs) {
        joinableQuickMatchRoomIndex.rebuildAll(registry.roomMap(), nowMs);
    }

    public void refreshJoinableQuickMatchIndexRoom(String roomId, long nowMs) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        DpRoomBO live = registry.get(roomId);
        if (live == null) {
            joinableQuickMatchRoomIndex.remove(roomId);
        } else {
            joinableQuickMatchRoomIndex.addOrRefresh(roomId, live, nowMs);
        }
    }

    public void refreshJoinableQmIndexThenSyncLobby(String roomId) {
        refreshJoinableQuickMatchIndexRoom(roomId, System.currentTimeMillis());
        syncLobbyForRoomId(roomId);
    }

    public void finalizeHallAfterRoomRemoved(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        roomChatPersistenceService.flushRoomToDatabase(roomId);
        joinableQuickMatchRoomIndex.remove(roomId);
        dpRoomHallService.deleteRoomSummary(roomId);
        gameRoomPushService.shutdownSubscriptionsForRoom(roomId);
    }

    public void syncLobbyForRoomId(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        DpRoomBO room = registry.get(roomId);
        if (room == null) {
            return;
        }
        dpRoomHallService.upsertRoomSummary(room);
    }
}
