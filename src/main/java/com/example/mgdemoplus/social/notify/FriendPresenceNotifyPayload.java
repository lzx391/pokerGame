package com.example.mgdemoplus.social.notify;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 好友列表 presence 增量推送（SSE {@code event: friendPresence}），与
 * {@code GET /dp/friends} 项上的 {@code presence} 字段口径一致。
 */
public final class FriendPresenceNotifyPayload {

    private final int friendUserId;
    private final String presence;
    private final String reason;

    public FriendPresenceNotifyPayload(int friendUserId, String presence, String reason) {
        this.friendUserId = friendUserId;
        this.presence = presence != null ? presence : "";
        this.reason = reason != null ? reason : "";
    }

    public int getFriendUserId() {
        return friendUserId;
    }

    public String getPresence() {
        return presence;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> toDataMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("friendUserId", friendUserId);
        m.put("presence", presence);
        if (!reason.isEmpty()) {
            m.put("reason", reason);
        }
        return m;
    }
}
