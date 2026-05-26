package com.example.mgdemoplus.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("dp_friend_link")
public class DpFriendLinkRow {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Integer userLowId;
    private Integer userHighId;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String friendNickname;
    @TableField(exist = false)
    private String friendAvatarUrl;
    @TableField(exist = false)
    private Integer friendUserId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserLowId() {
        return userLowId;
    }

    public void setUserLowId(Integer userLowId) {
        this.userLowId = userLowId;
    }

    public Integer getUserHighId() {
        return userHighId;
    }

    public void setUserHighId(Integer userHighId) {
        this.userHighId = userHighId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFriendNickname() {
        return friendNickname;
    }

    public void setFriendNickname(String friendNickname) {
        this.friendNickname = friendNickname;
    }

    public String getFriendAvatarUrl() {
        return friendAvatarUrl;
    }

    public void setFriendAvatarUrl(String friendAvatarUrl) {
        this.friendAvatarUrl = friendAvatarUrl;
    }

    public Integer getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(Integer friendUserId) {
        this.friendUserId = friendUserId;
    }
}
