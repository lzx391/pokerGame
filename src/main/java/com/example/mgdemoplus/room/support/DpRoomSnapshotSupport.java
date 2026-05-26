package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.roomchat.buffer.RoomChatEntry;
import com.example.mgdemoplus.utils.ResultUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Viewer-safe room snapshots and in-memory recent chat listing.
 */
public final class DpRoomSnapshotSupport {

    private final DpRoomRegistry registry;
    private final RoomChatBuffer roomChatBuffer;
    private final ObjectMapper objectMapper;
    private final DpRoomServiceCallbacks callbacks;
    private final DpSensitiveWordService sensitiveWordService;

    public DpRoomSnapshotSupport(
            DpRoomRegistry registry,
            RoomChatBuffer roomChatBuffer,
            ObjectMapper objectMapper,
            DpRoomServiceCallbacks callbacks,
            DpSensitiveWordService sensitiveWordService) {
        this.registry = registry;
        this.roomChatBuffer = roomChatBuffer;
        this.objectMapper = objectMapper;
        this.callbacks = callbacks;
        this.sensitiveWordService = sensitiveWordService;
    }

    public ResultUtil listRecentRoomChat(String roomId, String viewerNickname, int limit) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        DpRoomBO live = registry.get(roomId);
        if (live == null) {
            return null;
        }
        String nick = viewerNickname != null ? viewerNickname.trim() : "";
        if (nick.isEmpty() || !callbacks.isNicknameInRoom(live, nick)) {
            return ResultUtil.error().data("message", "您不在该房间内");
        }
        int lim = limit <= 0 ? 50 : Math.min(limit, RoomChatBuffer.MAX_PER_ROOM);
        List<RoomChatEntry> entries = roomChatBuffer.recent(roomId, lim);
        List<Map<String, Object>> items = new ArrayList<>();
        for (RoomChatEntry e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(e.getId()));
            row.put("nickname", e.getSenderNickname());
            row.put("text", sensitiveWordService.maskForChat(e.getBody()));
            row.put("serverTime", e.getServerTimeMs());
            if (e.getSenderUserId() != null) {
                row.put("senderUserId", e.getSenderUserId());
            }
            items.add(row);
        }
        return ResultUtil.ok().data("items", items);
    }

    public DpRoomBO getRoomSnapshotForViewer(String roomId, String viewerNickname) {
        DpRoomBO live = callbacks.getAllRooms(roomId);
        if (live == null) {
            return null;
        }
        if (viewerNickname != null && !viewerNickname.trim().isEmpty()
                && !callbacks.isNicknameInRoom(live, viewerNickname)) {
            return null;
        }
        return snapshotForViewerFromLive(live, viewerNickname);
    }
/**
 * 裁剪json给不同视角的人看
 * @param live
 * @param viewerNickname
 * @return
 */
    public DpRoomBO snapshotForViewerFromLive(DpRoomBO live, String viewerNickname) {
        if (live == null) {
            return null;
        }
        DpRoomBO copy = deepCopyRoomForSnapshot(live);
        sanitizeHoleCardsForViewer(copy, viewerNickname);
        attachViewerCarryIn(copy, live, viewerNickname);
        return copy;
    }

    private static void attachViewerCarryIn(DpRoomBO snapshot, DpRoomBO live, String viewerNickname) {
        if (snapshot == null) {
            return;
        }
        int carryIn = 0;
        if (live != null && viewerNickname != null) {
            String v = viewerNickname.trim();
            if (!v.isEmpty()) {
                Integer ci = live.getCarryInChips().get(v);
                if (ci != null && ci > 0) {
                    carryIn = ci;
                }
            }
        }
        snapshot.setMyCarryInChips(carryIn);
    }

    private DpRoomBO deepCopyRoomForSnapshot(DpRoomBO live) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(live), DpRoomBO.class);
        } catch (Exception e) {
            throw new IllegalStateException("room snapshot copy failed", e);
        }
    }

    private static void sanitizeHoleCardsForViewer(DpRoomBO room, String viewerNickname) {
        if (room == null || room.getPlayers() == null) {
            return;
        }
        String v = viewerNickname == null ? "" : viewerNickname.trim();
        String stage = room.getCurrentStage();
        boolean multiWayShowdown = true;
        boolean revealOthers = ("showdown".equals(stage) && multiWayShowdown)
                || ("settled".equals(stage) && room.isLastHandHoleCardsPublic() && multiWayShowdown);

        for (DpPlayer p : room.getPlayers()) {
            if (p == null) {
                continue;
            }
            if (!v.isEmpty() && v.equals(p.getNickname())) {
                continue;
            }
            boolean showCards = revealOthers && !p.isFold();
            if (!showCards && !v.equals(room.getOwner())) {
                p.setHoleCards(Collections.emptyList());
                p.setBestHandCards(Collections.emptyList());
                p.setHandRankName("");
                p.setHandRankDetail("");
            }
        }
    }
}
