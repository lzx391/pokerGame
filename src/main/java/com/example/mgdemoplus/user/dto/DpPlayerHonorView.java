package com.example.mgdemoplus.user.dto;

import com.example.mgdemoplus.leaderboard.vo.DpWeeklyLeaderboardPlacementView;
import java.math.BigDecimal;

/**
 * 公开的玩家荣誉战绩视图（可供局内其他玩家查看）。
 * 不含密码等敏感信息。
 */
public class DpPlayerHonorView {

    private int userId;
    private String nickname;
    private String avatarUrl;
    /** 头像最后更新时间（epoch 毫秒） */
    private Long avatarUpdatedAt;

    // ==== 生涯荣誉统计 ====
    private int royalFlushWins;
    private int straightFlushWins;
    private int fourOfAKindWins;
    private BigDecimal largestPotWon;
    private BigDecimal largestRoomNet;
    private int totalHandsPlayed;

    /** 本周手牌倍数周榜（board=hand） */
    private DpWeeklyLeaderboardPlacementView leaderboardWeeklyHand;
    /** 本周房间净赢倍数周榜（board=room） */
    private DpWeeklyLeaderboardPlacementView leaderboardWeeklyRoom;

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

    public DpWeeklyLeaderboardPlacementView getLeaderboardWeeklyHand() {
        return leaderboardWeeklyHand;
    }

    public void setLeaderboardWeeklyHand(DpWeeklyLeaderboardPlacementView leaderboardWeeklyHand) {
        this.leaderboardWeeklyHand = leaderboardWeeklyHand;
    }

    public DpWeeklyLeaderboardPlacementView getLeaderboardWeeklyRoom() {
        return leaderboardWeeklyRoom;
    }

    public void setLeaderboardWeeklyRoom(DpWeeklyLeaderboardPlacementView leaderboardWeeklyRoom) {
        this.leaderboardWeeklyRoom = leaderboardWeeklyRoom;
    }
}
