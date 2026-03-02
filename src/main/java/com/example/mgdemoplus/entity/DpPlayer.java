package com.example.mgdemoplus.entity;

public class DpPlayer {
    String nickname;    // 从用户表来
    boolean ready;
    private int chips = 50;   // 筹码
    private String card1;     // 牌1
    private String card2;     // 牌2
    private boolean fold =false;     // 是否弃牌
    private int bet=0;
    private long lastHeartBeat = System.currentTimeMillis();
    @Override
    public String toString() {
        return "DpPlayer{" +
                "nickname='" + nickname + '\'' +
                ", ready=" + ready +
                ", chips=" + chips +
                ", card1='" + card1 + '\'' +
                ", card2='" + card2 + '\'' +
                ", fold=" + fold +
                ", bet=" + bet +
                '}';
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public int getChips() {
        return chips;
    }

    public void setChips(int chips) {
        this.chips = chips;
    }

    public String getCard1() {
        return card1;
    }

    public void setCard1(String card1) {
        this.card1 = card1;
    }

    public String getCard2() {
        return card2;
    }

    public void setCard2(String card2) {
        this.card2 = card2;
    }

    public boolean isFold() {
        return fold;
    }

    public void setFold(boolean fold) {
        this.fold = fold;
    }

    public int getBet() {
        return bet;
    }

    public void setBet(int bet) {
        this.bet = bet;
    }
    public long getLastHeartBeat() { return lastHeartBeat; }
    public void setLastHeartBeat(long lastHeartBeat) { this.lastHeartBeat = lastHeartBeat; }
}
