package com.example.mgdemoplus.dto;

/**
 * 写入 Redis 的房间路由快照（JSON），供跨节点快速解析。
 */
public class DpRoomRoutePayload {
    private String roomId;
    private String shardId;
    private String wsRoute;
    private int status;
    private int playerCount;
    private int spectatorCount;
    private boolean passwordProtected;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getShardId() {
        return shardId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }

    public String getWsRoute() {
        return wsRoute;
    }

    public void setWsRoute(String wsRoute) {
        this.wsRoute = wsRoute;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public int getSpectatorCount() {
        return spectatorCount;
    }

    public void setSpectatorCount(int spectatorCount) {
        this.spectatorCount = spectatorCount;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }

    public void setPasswordProtected(boolean passwordProtected) {
        this.passwordProtected = passwordProtected;
    }
}
