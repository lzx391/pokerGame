package com.example.mgdemoplus.service;

import com.example.mgdemoplus.entity.DpRoomChatMessageRow;
import com.example.mgdemoplus.mapper.DpRoomChatMessageMapper;
import com.example.mgdemoplus.room.RoomChatBuffer;
import com.example.mgdemoplus.room.RoomChatEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 房间摘出 {@code roomMap} 时将局内聊天内存批量写入 {@code dp_room_chat_message}。
 */
@Service
public class DpRoomChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DpRoomChatPersistenceService.class);

    private final RoomChatBuffer roomChatBuffer;
    private final DpRoomChatMessageMapper roomChatMessageMapper;

    public DpRoomChatPersistenceService(RoomChatBuffer roomChatBuffer, DpRoomChatMessageMapper roomChatMessageMapper) {
        this.roomChatBuffer = roomChatBuffer;
        this.roomChatMessageMapper = roomChatMessageMapper;
    }

    @Transactional
    public void flushRoomToDatabase(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        List<RoomChatEntry> drained = roomChatBuffer.drain(roomId);
        if (drained.isEmpty()) {
            return;
        }
        List<DpRoomChatMessageRow> rows = new ArrayList<>(drained.size());
        for (RoomChatEntry e : drained) {
            DpRoomChatMessageRow row = new DpRoomChatMessageRow();
            row.setId(e.getId());
            row.setRoomId(e.getRoomId());
            row.setSenderUserId(e.getSenderUserId());
            row.setSenderNickname(e.getSenderNickname() != null ? e.getSenderNickname() : "");
            row.setBody(e.getBody());
            row.setServerTimeMs(e.getServerTimeMs());
            rows.add(row);
        }
        try {
            roomChatMessageMapper.insertBatch(rows);
            log.info("room chat flushed roomId={} count={}", roomId, rows.size());
        } catch (Exception ex) {
            log.error("room chat flush failed roomId={} count={}", roomId, rows.size(), ex);
        }
    }
}
