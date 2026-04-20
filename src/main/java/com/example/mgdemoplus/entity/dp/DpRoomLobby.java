package com.example.mgdemoplus.entity.dp;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 大厅房间摘要表 {@code dp_room_lobby}，与 {@link com.example.mgdemoplus.mapper.dp.DpRoomLobbyMapper} 注解 SQL 写入的数据一致；
 * 动态筛选 / 精确搜走 {@link com.example.mgdemoplus.mapper.dp.DpRoomLobbyMpMapper}（MyBatis-Plus），不经 Redis。
 */
@TableName("dp_room_lobby")
public class DpRoomLobby {

    @TableId("room_id")
    private String roomId;

    @TableField("owner_nickname")
    private String ownerNickname;

    /** 0 游戏中，1 已结束 */
    @TableField("room_status")
    private Integer roomStatus;

    @TableField("player_count")
    private Integer playerCount;

    @TableField("small_blind_chips")
    private Integer smallBlindChips;

    @TableField("big_blind_chips")
    private Integer bigBlindChips;

    @TableField("starting_stack_bb")
    private Integer startingStackBb;

    @TableField("password_protected")
    private Boolean passwordProtected;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getOwnerNickname() {
        return ownerNickname;
    }

    public void setOwnerNickname(String ownerNickname) {
        this.ownerNickname = ownerNickname;
    }

    public Integer getRoomStatus() {
        return roomStatus;
    }

    public void setRoomStatus(Integer roomStatus) {
        this.roomStatus = roomStatus;
    }

    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
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

    public Boolean getPasswordProtected() {
        return passwordProtected;
    }

    public void setPasswordProtected(Boolean passwordProtected) {
        this.passwordProtected = passwordProtected;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
