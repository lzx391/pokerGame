package com.example.mgdemoplus.entity;

import java.util.ArrayList;
import java.util.List;

public class DpPlayer {
    private String nickname;
    private boolean ready;
    private int chips = 500;       // 初始500积分 = 50BB
    private List<String> holeCards = new ArrayList<>();
    private boolean fold = false;
    private int bet = 0;//本轮
    private boolean isDealer = false;
    private int blind = 0;         // 0无 1小盲 2大盲
    private long lastHeartBeat = System.currentTimeMillis();
    private boolean acted = false;  // 本轮是否已行动

    private int totalBet = 0;  // 本手牌累计下注（跨阶段不清零，newHand时才清零）
    private boolean allIn = false;  // 是否已全下
    /**
     * 是否在本手牌中已主动离开（exitRoom），
     * 在本手结束前仍保留座位但视为已弃牌，开新一手时才真正清理出玩家列表。
     */
    private boolean leftThisHand = false;

    // getter & setter
    public boolean isActed() { return acted; }
    public void setActed(boolean acted) { this.acted = acted; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public int getChips() { return chips; }
    public void setChips(int chips) { this.chips = chips; }
    public List<String> getHoleCards() { return holeCards; }
    public void setHoleCards(List<String> holeCards) { this.holeCards = holeCards; }
    public boolean isFold() { return fold; }
    public void setFold(boolean fold) { this.fold = fold; }
    public int getBet() { return bet; }
    public void setBet(int bet) { this.bet = bet; }
    public boolean isDealer() { return isDealer; }
    public void setDealer(boolean dealer) { isDealer = dealer; }
    public int getBlind() { return blind; }
    public void setBlind(int blind) { this.blind = blind; }
    public long getLastHeartBeat() { return lastHeartBeat; }
    public void setLastHeartBeat(long lastHeartBeat) { this.lastHeartBeat = lastHeartBeat; }
    public int getTotalBet() { return totalBet; }
    public void setTotalBet(int totalBet) { this.totalBet = totalBet; }
    public boolean isAllIn() { return allIn; }
    public void setAllIn(boolean allIn) { this.allIn = allIn; }
    public boolean isLeftThisHand() { return leftThisHand; }
    public void setLeftThisHand(boolean leftThisHand) { this.leftThisHand = leftThisHand; }
}