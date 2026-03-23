package com.example.mgdemoplus.entity.dp;

/**
 * 表 dp_observed_hand_history 对应实体（仅用于 MyBatis 写入）。
 */
public class DpObservedHandHistory {
    private Long id;
    private String roomId;
    private Long handSeed;
    private Long startedAtMs;
    private Long endedAtMs;
    private Integer smallBlindChips;
    private Integer bigBlindChips;
    private String dealerNickname;
    private Integer mainPotBeforeSettlement;
    private Integer payloadVersion;
    private String payloadJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getPayloadVersion() {
        return payloadVersion;
    }

    public void setPayloadVersion(Integer payloadVersion) {
        this.payloadVersion = payloadVersion;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
