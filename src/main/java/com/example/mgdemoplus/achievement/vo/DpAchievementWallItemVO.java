package com.example.mgdemoplus.achievement.vo;

import com.example.mgdemoplus.utils.DpDateTimeSupport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class DpAchievementWallItemVO {
    private int id;
    private String code;
    private String title;
    private String description;
    private int sortOrder;
    private boolean unlocked;
    private LocalDateTime unlockedAt;
    /** 已解锁且首次解锁牌谱存在时非空，供成就墙「查看回放」 */
    private Long handHistoryId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    @JsonIgnore
    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(LocalDateTime unlockedAt) {
        this.unlockedAt = unlockedAt;
    }

    /** API 与牌谱 {@code endedAtMs} 一致：epoch 毫秒，前端 {@code new Date(ms)} 按本地时区展示。 */
    @JsonProperty("unlockedAt")
    public Long getUnlockedAtEpochMs() {
        return DpDateTimeSupport.toEpochMilli(unlockedAt);
    }

    public Long getHandHistoryId() {
        return handHistoryId;
    }

    public void setHandHistoryId(Long handHistoryId) {
        this.handHistoryId = handHistoryId;
    }
}
