package com.example.mgdemoplus.room.dto;

import java.util.ArrayList;
import java.util.List;

/** {@code POST /dpRoom/setNextHandDeckPrefix} 请求体。 */
public class SetNextHandDeckPrefixRequest {
    private String roomId;
    /** 实验排牌访问密码（与 {@code EXPERIMENTAL_DECK_PRESET_PASSWORD} 一致）。 */
    private String experimentalPassword;
    private List<String> cards = new ArrayList<>();

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getExperimentalPassword() {
        return experimentalPassword;
    }

    public void setExperimentalPassword(String experimentalPassword) {
        this.experimentalPassword = experimentalPassword;
    }

    public List<String> getCards() {
        return cards;
    }

    public void setCards(List<String> cards) {
        this.cards = cards;
    }
}
