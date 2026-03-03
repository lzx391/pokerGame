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

    // getter & setter
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
}