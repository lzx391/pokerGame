package com.example.mgdemoplus.social.notify;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 成就首次解锁推送（SSE {@code event: achievement_unlocked}）。
 */
public final class AchievementUnlockNotifyPayload {

    private final String code;
    private final String title;
    private final String description;

    public AchievementUnlockNotifyPayload(String code, String title, String description) {
        this.code = code != null ? code : "";
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> toDataMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("description", description);
        return m;
    }
}
