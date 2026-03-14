package com.example.mgdemoplus.entity;

import com.example.mgdemoplus.entity.DpPlayer;

import java.util.ArrayList;
import java.util.List;

public class DpRoom {
    private String roomId;
    private String owner;
    private List<DpPlayer> players = new ArrayList<>();
    private boolean isPlaying = false;
    /**
     * 观众席：当前在房间内但不在牌桌上的昵称列表
     */
    private List<String> spectators = new ArrayList<>();

    // 德扑核心
    private String currentStage = "preflop";
    private List<String> communityCards = new ArrayList<>();
    private List<String> deck = new ArrayList<>();
    private int pot = 0;
    private int currentBetToCall = 0;
    private static final int CHIPS =500;//全局筹码设置
    private static final int SB_CHIPS =5;//小盲筹码设置
    private static final int BB_CHIPS =10;//大盲筹码设置


    // 行动顺序
    private int lastDealerIndex =0;
    private int currentActorIndex = -1;
    private long lastActionTime = 0;
    private static final int ACTION_TIMEOUT = 30000; // 30秒
    private static final int HEART_TIMEOUT = 20000; // 20秒
    private List<DpPot> pots = new ArrayList<>();
    // 等待在下一局加入的玩家昵称列表（当前局仅旁观）
    private List<String> waitNextHand = new ArrayList<>();

    /**
     * 结算后准备阶段的截止时间戳（毫秒）。为 0 表示当前没有准备倒计时。
     */
    private long readyDeadline = 0L;
    // getter & setter
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public List<DpPlayer> getPlayers() { return players; }
    public void setPlayers(List<DpPlayer> players) { this.players = players; }
    public List<String> getSpectators() { return spectators; }
    public void setSpectators(List<String> spectators) { this.spectators = spectators; }
    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public List<String> getCommunityCards() { return communityCards; }
    public void setCommunityCards(List<String> communityCards) { this.communityCards = communityCards; }
    public List<String> getDeck() { return deck; }
    public void setDeck(List<String> deck) { this.deck = deck; }
    public int getPot() { return pot; }
    public void setPot(int pot) { this.pot = pot; }
    public int getCurrentBetToCall() { return currentBetToCall; }
    public void setCurrentBetToCall(int currentBetToCall) { this.currentBetToCall = currentBetToCall; }
    public int getCurrentActorIndex() { return currentActorIndex; }
    public void setCurrentActorIndex(int currentActorIndex) { this.currentActorIndex = currentActorIndex; }
    public long getLastActionTime() { return lastActionTime; }
    public void setLastActionTime(long lastActionTime) { this.lastActionTime = lastActionTime; }
    public static int getActionTimeout() { return ACTION_TIMEOUT; }
    public List<DpPot> getPots() { return pots; }
    public void setPots(List<DpPot> pots) { this.pots = pots; }
    public List<String> getWaitNextHand() { return waitNextHand; }
    public void setWaitNextHand(List<String> waitNextHand) { this.waitNextHand = waitNextHand; }
    public long getReadyDeadline() { return readyDeadline; }
    public void setReadyDeadline(long readyDeadline) { this.readyDeadline = readyDeadline; }
    public static int getHeartTimeout() { return HEART_TIMEOUT; }
    public static int getChips() { return CHIPS; }
    public static int getSBChips() { return SB_CHIPS; }
    public static int getBBChips() { return BB_CHIPS; }

    public int getLastDealerIndex() {
        return lastDealerIndex;
    }

    public void setLastDealerIndex(int lastDealerIndex) {
        this.lastDealerIndex = lastDealerIndex;
    }
}