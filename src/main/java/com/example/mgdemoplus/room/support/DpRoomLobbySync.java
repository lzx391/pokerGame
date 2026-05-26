package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quick-match joinable index maintenance and {@code dp_room_lobby} summary sync after room mutations.
 */
public final class DpRoomLobbySync {

    private static final Logger log = LoggerFactory.getLogger(DpRoomLobbySync.class);

    private final DpRoomRegistry registry;
    private final JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex;
    private final DpRoomHallService dpRoomHallService;
    private final DpGameRoomPushService gameRoomPushService;
    private final DpRoomChatPersistenceService roomChatPersistenceService;
    /** Last upserted lobby summary fingerprint per roomId (P0-2 dedup). */
    private final ConcurrentHashMap<String, String> lobbySummaryFingerprints = new ConcurrentHashMap<>();

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

    /**
     * Unified post-mutation hook: refresh quick-match index and/or lobby row (with summary fingerprint skip).
     */
    public void afterRoomMutation(String roomId, Set<DpRoomMutationEffect> effects) {
        if (roomId == null || roomId.isEmpty() || effects == null || effects.isEmpty()) {
            return;
        }
        boolean index = effects.contains(DpRoomMutationEffect.INDEX)
                || effects.contains(DpRoomMutationEffect.BOTH);
        boolean lobby = effects.contains(DpRoomMutationEffect.LOBBY)
                || effects.contains(DpRoomMutationEffect.BOTH);
        if (index) {
            refreshJoinableQuickMatchIndexRoom(roomId, System.currentTimeMillis());
        }
        if (lobby) {
            syncLobbyForRoomIdIfChanged(roomId);
        }
    }

    public void afterRoomMutation(String roomId, DpRoomMutationEffect effect) {
        if (effect == null) {
            return;
        }
        afterRoomMutation(roomId, EnumSet.of(effect));
    }

    public void refreshJoinableQmIndexThenSyncLobby(String roomId) {
        afterRoomMutation(roomId, DpRoomMutationEffect.BOTH);
    }

    public void finalizeHallAfterRoomRemoved(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        lobbySummaryFingerprints.remove(roomId);
        roomChatPersistenceService.flushRoomToDatabase(roomId);
        joinableQuickMatchRoomIndex.remove(roomId);
        dpRoomHallService.deleteRoomSummary(roomId);
        gameRoomPushService.shutdownSubscriptionsForRoom(roomId);
    }

    public void syncLobbyForRoomId(String roomId) {
        syncLobbyForRoomIdIfChanged(roomId);
    }

    /**
     * Upsert {@code dp_room_lobby} only when {@link #lobbySummaryFingerprint(DpRoomBO)} differs from last write.
     */
    void syncLobbyForRoomIdIfChanged(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        DpRoomBO room = registry.get(roomId);
        if (room == null) {
            return;
        }
        String fp = lobbySummaryFingerprint(room);
        String prev = lobbySummaryFingerprints.put(roomId, fp);
        if (prev != null && prev.equals(fp)) {
            log.trace("skip lobby upsert roomId={} fingerprint unchanged", roomId);
            return;
        }
        dpRoomHallService.upsertRoomSummary(room);
    }

    /**
     * Fingerprint aligned with {@link com.example.mgdemoplus.lobby.impl.DpRoomHallServiceImpl#toSummary}.
     */
    static String lobbySummaryFingerprint(DpRoomBO room) {
        if (room == null) {
            return "";
        }
        int playerSize = 0;
        if (room.getPlayers() != null) {
            for (DpPlayer p : room.getPlayers()) {
                if (p != null && !p.isLeftThisHand()) {
                    playerSize++;
                }
            }
        }
        return room.getRoomId()
                + '|' + nullToEmpty(room.getOwner())
                + '|' + playerSize
                + '|' + room.getSmallBlindChips()
                + '|' + room.getBigBlindChips()
                + '|' + room.getStartingStackBb()
                + '|' + room.isPasswordProtected()
                + '|' + room.getMaxSeatCount();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
