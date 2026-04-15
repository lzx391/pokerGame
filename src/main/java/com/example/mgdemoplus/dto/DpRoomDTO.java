package com.example.mgdemoplus.dto;

public class DpRoomDTO {
    private String roomId;
    private String owner;
    private int playerSize;
    private int smallBlindChips;
    private int bigBlindChips;
    /** 每人初始筹码对应的大盲倍数（如 50 表示 50BB 带入） */
    private int startingStackBb;
    private boolean passwordProtected;
    /** 跨节点：直连该房间所在实例的 WebSocket 完整 URL（方案二）；仅本机列表时可为空 */
    private String wsRoute;
    /** 游戏实例 ID，与注册表一致 */
    private String shardId;

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

    public String getWsRoute() {
        return wsRoute;
    }

    public void setWsRoute(String wsRoute) {
        this.wsRoute = wsRoute;
    }

    public String getShardId() {
        return shardId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }
}


