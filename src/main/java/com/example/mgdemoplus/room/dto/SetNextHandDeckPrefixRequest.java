package com.example.mgdemoplus.room.dto;

import java.util.ArrayList;
import java.util.List;

/** {@code POST /dpRoom/setNextHandDeckPrefix} 请求体。 */
public class SetNextHandDeckPrefixRequest {
    private String roomId;
    private String requesterNickname;
    private List<String> cards = new ArrayList<>();

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRequesterNickname() {
        return requesterNickname;
    }

    public void setRequesterNickname(String requesterNickname) {
        this.requesterNickname = requesterNickname;
    }

    public List<String> getCards() {
        return cards;
    }

    public void setCards(List<String> cards) {
        this.cards = cards;
    }
}
