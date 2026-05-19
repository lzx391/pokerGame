package com.example.mgdemoplus.history.entity;

/**
 * 表 dp_observed_hand_participant（牌谱 ↔ 用户）。
 */
public class DpObservedHandParticipant {
    private Long id;
    private Long handHistoryId;
    /** dp_user.id；未解析到则为 null。 */
    private Integer userId;
    private String nicknameSnapshot;
    private Integer seatIndex;
    private Boolean dealer;
    private Integer blindPos;
    private Integer netChips;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHandHistoryId() {
        return handHistoryId;
    }

    public void setHandHistoryId(Long handHistoryId) {
        this.handHistoryId = handHistoryId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getNicknameSnapshot() {
        return nicknameSnapshot;
    }

    public void setNicknameSnapshot(String nicknameSnapshot) {
        this.nicknameSnapshot = nicknameSnapshot;
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
