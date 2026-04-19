package com.example.mgdemoplus.vo;

public class DpRoomVO {
    private String roomId;
    private String owner;
    private int playerSize;
    private int smallBlindChips;
    private int bigBlindChips;
    /** 每人初始筹码对应的大盲倍数（如 50 表示 50BB 带入） */
    private int startingStackBb;
    private boolean passwordProtected;

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

    public int getSmallBlindChips() {
        return smallBlindChips;
    }

    public void setSmallBlindChips(int smallBlindChips) {
        this.smallBlindChips = smallBlindChips;
    }

    public int getBigBlindChips() {
        return bigBlindChips;
    }

    public void setBigBlindChips(int bigBlindChips) {
        this.bigBlindChips = bigBlindChips;
    }

    public int getStartingStackBb() {
        return startingStackBb;
    }

    public void setStartingStackBb(int startingStackBb) {
        this.startingStackBb = startingStackBb;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }

    public void setPasswordProtected(boolean passwordProtected) {
        this.passwordProtected = passwordProtected;
    }
}


