package com.example.mgdemoplus.dto;

import com.example.mgdemoplus.entity.DpPlayer;

import java.util.ArrayList;
import java.util.List;

public class DpRoomDTO {
    private String roomId;
    private String owner;
    private int playerSize ;

    @Override
    public String toString() {
        return "DpRoomDTO{" +
                "roomId='" + roomId + '\'' +
                ", owner='" + owner + '\'' +
                ", playerSize=" + playerSize +
                '}';
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public int getPlayerSize() {
        return playerSize;
    }

    public void setPlayerSize(int playerSize) {
        this.playerSize = playerSize;
    }
}


