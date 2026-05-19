package com.example.mgdemoplus.roomchat.buffer;

/**
 * 局内聊天单条内存记录（摘房时批量写入 {@code dp_room_chat_message}）。
 */
public final class RoomChatEntry {
    private final long id;
    private final String roomId;
    private final Integer senderUserId;
    private final String senderNickname;
    private final String body;
    private final long serverTimeMs;

    public RoomChatEntry(
            long id,
            String roomId,
            Integer senderUserId,
            String senderNickname,
            String body,
            long serverTimeMs) {
        this.id = id;
        this.roomId = roomId;
        this.senderUserId = senderUserId;
        this.senderNickname = senderNickname;
        this.body = body;
        this.serverTimeMs = serverTimeMs;
    }

    public long getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public Integer getSenderUserId() {
        return senderUserId;
    }

    public String getSenderNickname() {
        return senderNickname;
    }

    public String getBody() {
        return body;
    }

    public long getServerTimeMs() {
        return serverTimeMs;
    }
}
