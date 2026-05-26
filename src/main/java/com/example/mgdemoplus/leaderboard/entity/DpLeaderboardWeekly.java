package com.example.mgdemoplus.leaderboard.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DpLeaderboardWeekly {

    private LocalDate weekMonday;
    private int userId;
    private BigDecimal bestHandMultiplier;
    private BigDecimal bestRoomMultiplier;
    private LocalDateTime updatedAt;

    public LocalDate getWeekMonday() {
        return weekMonday;
    }

    public void setWeekMonday(LocalDate weekMonday) {
        this.weekMonday = weekMonday;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public BigDecimal getBestHandMultiplier() {
        return bestHandMultiplier;
    }

    public void setBestHandMultiplier(BigDecimal bestHandMultiplier) {
        this.bestHandMultiplier = bestHandMultiplier;
    }

    public BigDecimal getBestRoomMultiplier() {
        return bestRoomMultiplier;
    }

    public void setBestRoomMultiplier(BigDecimal bestRoomMultiplier) {
        this.bestRoomMultiplier = bestRoomMultiplier;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
