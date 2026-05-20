package com.example.mgdemoplus.user.dto;

/**
 * 个人资料只读视图：不含密码哈希，含生涯牌局荣誉统计。
 */
public class DpUserProfileView {

    private int id;
    private String nickname;
    private String avatarUrl;
    /** 是否已设置登录密码（库内非空） */
    private boolean passwordSet;

    // ==== 生涯牌局荣誉统计（来自 dp_user_stats，可能为 null 表示尚无对局记录） ====
    private Integer royalFlushWins;
    private Integer straightFlushWins;
    private Integer fourOfAKindWins;
    private Integer largestPotWon;
    private Integer largestRoomNet;
    private Integer totalHandsPlayed;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public void setPasswordSet(boolean passwordSet) {
        this.passwordSet = passwordSet;
    }

    public Integer getRoyalFlushWins() {
        return royalFlushWins;
    }

    public void setRoyalFlushWins(Integer royalFlushWins) {
        this.royalFlushWins = royalFlushWins;
    }

    public Integer getStraightFlushWins() {
        return straightFlushWins;
    }

    public void setStraightFlushWins(Integer straightFlushWins) {
        this.straightFlushWins = straightFlushWins;
    }

    public Integer getFourOfAKindWins() {
        return fourOfAKindWins;
    }

    public void setFourOfAKindWins(Integer fourOfAKindWins) {
        this.fourOfAKindWins = fourOfAKindWins;
    }

    public Integer getLargestPotWon() {
        return largestPotWon;
    }

    public void setLargestPotWon(Integer largestPotWon) {
        this.largestPotWon = largestPotWon;
    }

    public Integer getLargestRoomNet() {
        return largestRoomNet;
    }

    public void setLargestRoomNet(Integer largestRoomNet) {
        this.largestRoomNet = largestRoomNet;
    }

    public Integer getTotalHandsPlayed() {
        return totalHandsPlayed;
    }

    public void setTotalHandsPlayed(Integer totalHandsPlayed) {
        this.totalHandsPlayed = totalHandsPlayed;
    }
}
