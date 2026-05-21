package com.example.mgdemoplus.user.dto;

import java.math.BigDecimal;

/**
 * 公开的玩家荣誉战绩视图（可供局内其他玩家查看）。
 * 不含密码等敏感信息。
 */
public class DpPlayerHonorView {

    private int userId;
    private String nickname;
    private String avatarUrl;

    // ==== 生涯荣誉统计 ====
    private int royalFlushWins;
    private int straightFlushWins;
    private int fourOfAKindWins;
    private BigDecimal largestPotWon;
    private BigDecimal largestRoomNet;
    private int totalHandsPlayed;

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
}
