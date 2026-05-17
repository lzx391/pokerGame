package com.example.mgdemoplus.social;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大厅社交通知 SSE / REST 摘要载荷（与 {@code notify} 事件 data 一致）。
 */
public final class SocialNotifyPayload {

    private final int mailboxUnread;
    private final long friendChatUnreadTotal;
    private final Map<String, Integer> friendChatUnreadByFriendUserId;

    public SocialNotifyPayload(
            int mailboxUnread,
            long friendChatUnreadTotal,
            Map<String, Integer> friendChatUnreadByFriendUserId) {
        this.mailboxUnread = mailboxUnread;
        this.friendChatUnreadTotal = friendChatUnreadTotal;
        this.friendChatUnreadByFriendUserId =
                friendChatUnreadByFriendUserId != null
                        ? Map.copyOf(friendChatUnreadByFriendUserId)
                        : Map.of();
    }

    public int getMailboxUnread() {
        return mailboxUnread;
    }

    public long getFriendChatUnreadTotal() {
        return friendChatUnreadTotal;
    }

    public Map<String, Integer> getFriendChatUnreadByFriendUserId() {
        return friendChatUnreadByFriendUserId;
    }

    public Map<String, Object> toDataMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mailboxUnread", mailboxUnread);
        m.put("friendChatUnreadTotal", friendChatUnreadTotal);
        m.put("friendChatUnreadByFriendUserId", new LinkedHashMap<>(friendChatUnreadByFriendUserId));
        m.put("perFriend", buildPerFriendList());
        return m;
    }

    /** 与 {@code GET /dp/friends/chat-unread-summary} 的 perFriend 数组形状一致。 */
    private List<Map<String, Object>> buildPerFriendList() {
        List<Map<String, Object>> perFriend = new ArrayList<>();
        for (Map.Entry<String, Integer> e : friendChatUnreadByFriendUserId.entrySet()) {
            int count = e.getValue() != null ? e.getValue() : 0;
            if (count <= 0) {
                continue;
            }
            int userId;
            try {
                userId = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("count", count);
            perFriend.add(row);
        }
        return perFriend;
    }
}
