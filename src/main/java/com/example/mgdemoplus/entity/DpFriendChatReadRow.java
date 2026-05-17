package com.example.mgdemoplus.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("dp_friend_chat_read")
public class DpFriendChatReadRow {
    @TableId("user_id")
    private Integer userId;
    private Integer peerUserId;
    private Long lastReadMessageId;
    private LocalDateTime updatedAt;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(Integer peerUserId) {
        this.peerUserId = peerUserId;
    }

    public Long getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void setLastReadMessageId(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
