package com.example.mgdemoplus.entity;

import java.util.List;

public class DpRoom {
    String roomId;       // 房间号
    String owner;        // 房主昵称
    List<DpPlayer> players; // 所有玩家
    private Boolean isPlaying=false;     // 是否游戏中


    @Override
    public String toString() {
        return "DpRoom{" +
                "roomId='" + roomId + '\'' +
                ", owner='" + owner + '\'' +
                ", players=" + players +
                ", isPlaying=" + isPlaying +
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

    public List<DpPlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<DpPlayer> players) {
        this.players = players;
    }

    public Boolean getPlaying() {
        return isPlaying;
    }

    public void setPlaying(Boolean playing) {
        isPlaying = playing;
    }
}
