package com.example.mgdemoplus.common.entity;

import java.math.BigDecimal;

/**
 * 玩家生涯牌局荣誉统计（与 dp_user 垂直分表，共享 user_id）。
 * {@link #largestPotWon} 为单局净赢带入倍数；{@link #largestRoomNet} 为单房间净赢带入倍数。
 */
public class DpUserStats {

    private int userId;
    private int royalFlushWins;
    private int straightFlushWins;
    private int fourOfAKindWins;
    private BigDecimal largestPotWon;
    private BigDecimal largestRoomNet;
    private int totalHandsPlayed;
    /** 周榜前三累计次数（hand / room 各榜独立 +1） */
    private int leaderboardTopCount;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getRoyalFlushWins() {
        return royalFlushWins;
    }

    public void setRoyalFlushWins(int royalFlushWins) {
        this.royalFlushWins = royalFlushWins;
    }

    public int getStraightFlushWins() {
        return straightFlushWins;
    }

    public void setStraightFlushWins(int straightFlushWins) {
        this.straightFlushWins = straightFlushWins;
    }

    public int getFourOfAKindWins() {
        return fourOfAKindWins;
    }

    public void setFourOfAKindWins(int fourOfAKindWins) {
        this.fourOfAKindWins = fourOfAKindWins;
    }

    public BigDecimal getLargestPotWon() {
        return largestPotWon;
    }

    public void setLargestPotWon(BigDecimal largestPotWon) {
        this.largestPotWon = largestPotWon;
    }

    public BigDecimal getLargestRoomNet() {
        return largestRoomNet;
    }

    public void setLargestRoomNet(BigDecimal largestRoomNet) {
        this.largestRoomNet = largestRoomNet;
    }

    public int getTotalHandsPlayed() {
        return totalHandsPlayed;
    }

    public void setTotalHandsPlayed(int totalHandsPlayed) {
        this.totalHandsPlayed = totalHandsPlayed;
    }

    public int getLeaderboardTopCount() {
        return leaderboardTopCount;
    }

    public void setLeaderboardTopCount(int leaderboardTopCount) {
        this.leaderboardTopCount = leaderboardTopCount;
    }
}
