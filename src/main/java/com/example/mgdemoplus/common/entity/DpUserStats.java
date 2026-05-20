package com.example.mgdemoplus.common.entity;

/**
 * 玩家生涯牌局荣誉统计（与 dp_user 垂直分表，共享 user_id）。
 * 纯战绩记录，非积分/货币。
 */
public class DpUserStats {

    private int userId;
    private int royalFlushWins;
    private int straightFlushWins;
    private int fourOfAKindWins;
    private int largestPotWon;
    private int largestRoomNet;
    private int totalHandsPlayed;

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

    public int getLargestPotWon() {
        return largestPotWon;
    }

    public void setLargestPotWon(int largestPotWon) {
        this.largestPotWon = largestPotWon;
    }

    public int getLargestRoomNet() {
        return largestRoomNet;
    }

    public void setLargestRoomNet(int largestRoomNet) {
        this.largestRoomNet = largestRoomNet;
    }

    public int getTotalHandsPlayed() {
        return totalHandsPlayed;
    }

    public void setTotalHandsPlayed(int totalHandsPlayed) {
        this.totalHandsPlayed = totalHandsPlayed;
    }
}
