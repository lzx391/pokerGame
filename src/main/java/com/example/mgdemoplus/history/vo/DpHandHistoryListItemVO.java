package com.example.mgdemoplus.history.vo;

/**
 * 历史对局列表单行（参与者维度 JOIN 牌谱主表摘要）。
 */
public class DpHandHistoryListItemVO {
    private Long handHistoryId;
    private String roomId;
    private Long handSeed;
    private Long startedAtMs;
    private Long endedAtMs;
    private Integer smallBlindChips;
    private Integer bigBlindChips;
    private String dealerNickname;
    private Integer mainPotBeforeSettlement;
    private Integer seatIndex;
    private Boolean dealer;
    private Integer blindPos;
    private Integer netChips;

    public Long getHandHistoryId() {
        return handHistoryId;
    }

    public void setHandHistoryId(Long handHistoryId) {
        this.handHistoryId = handHistoryId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Long getHandSeed() {
        return handSeed;
    }

    public void setHandSeed(Long handSeed) {
        this.handSeed = handSeed;
    }

    public Long getStartedAtMs() {
        return startedAtMs;
    }

    public void setStartedAtMs(Long startedAtMs) {
        this.startedAtMs = startedAtMs;
    }

    public Long getEndedAtMs() {
        return endedAtMs;
    }

    public void setEndedAtMs(Long endedAtMs) {
        this.endedAtMs = endedAtMs;
    }

    public Integer getSmallBlindChips() {
        return smallBlindChips;
    }

    public void setSmallBlindChips(Integer smallBlindChips) {
        this.smallBlindChips = smallBlindChips;
    }

    public Integer getBigBlindChips() {
        return bigBlindChips;
    }

    public void setBigBlindChips(Integer bigBlindChips) {
        this.bigBlindChips = bigBlindChips;
    }

    public String getDealerNickname() {
        return dealerNickname;
    }

    public void setDealerNickname(String dealerNickname) {
        this.dealerNickname = dealerNickname;
    }

    public Integer getMainPotBeforeSettlement() {
        return mainPotBeforeSettlement;
    }

    public void setMainPotBeforeSettlement(Integer mainPotBeforeSettlement) {
        this.mainPotBeforeSettlement = mainPotBeforeSettlement;
    }

    public Integer getSeatIndex() {
        return seatIndex;
    }

    public void setSeatIndex(Integer seatIndex) {
        this.seatIndex = seatIndex;
    }

    public Boolean getDealer() {
        return dealer;
    }

    public void setDealer(Boolean dealer) {
        this.dealer = dealer;
    }

    public Integer getBlindPos() {
        return blindPos;
    }

    public void setBlindPos(Integer blindPos) {
        this.blindPos = blindPos;
    }

    public Integer getNetChips() {
        return netChips;
    }

    public void setNetChips(Integer netChips) {
        this.netChips = netChips;
    }
}
