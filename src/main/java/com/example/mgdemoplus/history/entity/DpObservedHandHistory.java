package com.example.mgdemoplus.history.entity;

/**
 * 表 dp_observed_hand_history 对应实体（仅用于 MyBatis 写入）。
 */
public class DpObservedHandHistory {
    private Long id;
    private String roomId;//房间ID
    private Long handSeed;//手牌种子
    private Long startedAtMs;//开始时间
    private Long endedAtMs;//结束时间
    private Integer smallBlindChips;//小盲注
    private Integer bigBlindChips;//大盲注
    private String dealerNickname;//庄家昵称
    private Integer mainPotBeforeSettlement;//结算前主池
    private Integer payloadVersion;//负载版本
    private String payloadJson;//负载数据

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
