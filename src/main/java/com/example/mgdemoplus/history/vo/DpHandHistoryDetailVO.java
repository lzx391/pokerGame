package com.example.mgdemoplus.history.vo;

import java.util.Map;

/**
 * 单条牌谱详情（payload 为解析后的 JSON 对象；他人底牌已在服务端脱敏）。
 */
public class DpHandHistoryDetailVO {
    //本类的作用是：将dp_observed_hand_history表中的数据转换为DTO对象
    private Long handHistoryId;
    private String roomId;
    private Long handSeed;
    private Long startedAtMs;
    private Long endedAtMs;
    private Integer smallBlindChips;
    private Integer bigBlindChips;
    private String dealerNickname;
    private Integer mainPotBeforeSettlement;
    private Integer payloadVersion;
    /** seatsAtStart、boardsByStreet、actions、potsBeforeSettlement、holeCardsAtEnd、netChipsChange */
    private Map<String, Object> payload;

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

    public Integer getPayloadVersion() {
        return payloadVersion;
    }

    public void setPayloadVersion(Integer payloadVersion) {
        this.payloadVersion = payloadVersion;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
