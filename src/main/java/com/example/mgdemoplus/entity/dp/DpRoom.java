package com.example.mgdemoplus.entity.dp;

public class DpRoom {
    private String roomId;
    private String owner;
    private int playerSize;
    private int smallBlindChips;
    private int bigBlindChips;
    /** 每人初始筹码对应的大盲倍数（如 50 表示 50BB 带入） */
    private int startingStackBb;
    private boolean passwordProtected;
    /** 一桌最多玩家数（建房时指定，与大厅表 max_seat_count 一致） */
    private int maxSeatCount = 9;

    @Override
    public String toString() {
        return "DpRoomDTO{" +
                "roomId='" + roomId + '\'' +
                ", owner='" + owner + '\'' +
                ", playerSize=" + playerSize +
                ", maxSeatCount=" + maxSeatCount +
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

    public int getMaxSeatCount() {
        return maxSeatCount;
    }

    public void setMaxSeatCount(int maxSeatCount) {
        this.maxSeatCount = maxSeatCount;
    }
}


