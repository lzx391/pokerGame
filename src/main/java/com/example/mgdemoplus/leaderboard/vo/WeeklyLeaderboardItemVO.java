package com.example.mgdemoplus.leaderboard.vo;

import java.math.BigDecimal;

public class WeeklyLeaderboardItemVO {

    private int rank;
    private int userId;
    private String nickname;
    private String avatarUrl;
    private Long avatarUpdatedAt;
    private BigDecimal multiplier;

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Long getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(Long avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }
}
