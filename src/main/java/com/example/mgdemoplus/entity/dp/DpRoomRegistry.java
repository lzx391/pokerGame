package com.example.mgdemoplus.entity.dp;

import java.time.LocalDateTime;

/**
 * 房间在注册表中的元数据（跨节点发现），与内存 {@link DpRoom} 分离。
 */
public class DpRoomRegistry {
    private Long id;
    private String roomId;
    private String gameCode;
    private String shardId;
    private String wsRoute;
    /** 0=等待 1=游戏中 2=已关闭 */
    private Integer status;
    private Integer hasPassword;
    private Integer maxSeats;
    private Integer playerCount;
    private Integer spectatorCount;
    private String ownerNickname;
    private Integer smallBlindChips;
    private Integer bigBlindChips;
    private Integer startingStackBb;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(Integer hasPassword) {
        this.hasPassword = hasPassword;
    }

    public Integer getMaxSeats() {
        return maxSeats;
    }

    public void setMaxSeats(Integer maxSeats) {
        this.maxSeats = maxSeats;
    }

    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }

    public Integer getSpectatorCount() {
        return spectatorCount;
    }

    public void setSpectatorCount(Integer spectatorCount) {
        this.spectatorCount = spectatorCount;
    }

    public String getOwnerNickname() {
        return ownerNickname;
    }

    public void setOwnerNickname(String ownerNickname) {
        this.ownerNickname = ownerNickname;
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

    public Integer getStartingStackBb() {
        return startingStackBb;
    }

    public void setStartingStackBb(Integer startingStackBb) {
        this.startingStackBb = startingStackBb;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
