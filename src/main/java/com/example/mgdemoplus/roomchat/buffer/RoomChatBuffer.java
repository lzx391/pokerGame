package com.example.mgdemoplus.roomchat.buffer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存活房间局内聊天内存缓冲；摘房时由 {@link com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService} 落库并 drain。
 */
@Component
public class RoomChatBuffer {

    public static final int MAX_PER_ROOM = 500;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<RoomChatEntry>> byRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> idSeqByRoom = new ConcurrentHashMap<>();

    public RoomChatEntry append(
            String roomId,
            Integer senderUserId,
            String senderNickname,
            String body,
            long serverTimeMs) {
        if (roomId == null || roomId.isEmpty() || senderNickname == null || body == null) {
            return null;
        }
        long id = idSeqByRoom.computeIfAbsent(roomId, k -> new AtomicLong(0)).incrementAndGet();
        RoomChatEntry entry =
                new RoomChatEntry(id, roomId, senderUserId, senderNickname, body, serverTimeMs);
        CopyOnWriteArrayList<RoomChatEntry> list = byRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        list.add(entry);
        trimExcess(list);
        return entry;
    }

    public List<RoomChatEntry> recent(String roomId, int limit) {
        if (roomId == null || roomId.isEmpty() || limit <= 0) {
            return List.of();
        }
        CopyOnWriteArrayList<RoomChatEntry> list = byRoom.get(roomId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        int n = list.size();
        int from = Math.max(0, n - limit);
        return List.copyOf(list.subList(from, n));
    }

    public boolean hasRoom(String roomId) {
        return roomId != null && byRoom.containsKey(roomId);
    }

    /**
     * 摘房：取出并移除该房全部缓冲（供批量 INSERT）。
     */
    public List<RoomChatEntry> drain(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return List.of();
        }
        CopyOnWriteArrayList<RoomChatEntry> list = byRoom.remove(roomId);
        idSeqByRoom.remove(roomId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return List.copyOf(list);
    }

    private static void trimExcess(CopyOnWriteArrayList<RoomChatEntry> list) {
        while (list.size() > MAX_PER_ROOM) {
            list.remove(0);
        }
    }
}
