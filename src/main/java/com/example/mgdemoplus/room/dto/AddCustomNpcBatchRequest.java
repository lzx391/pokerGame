package com.example.mgdemoplus.room.dto;

import com.example.mgdemoplus.npc.CustomNpcStyleProfileDto;

/**
 * {@code POST /dpRoom/addCustomNpcBatch} 请求体（9a：同批共用一份 profile）。
 */
public class AddCustomNpcBatchRequest {
    private String roomId;
    private int count;
    private String requesterNickname;
    private CustomNpcStyleProfileDto profile;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getRequesterNickname() {
        return requesterNickname;
    }

    public void setRequesterNickname(String requesterNickname) {
        this.requesterNickname = requesterNickname;
    }

    public CustomNpcStyleProfileDto getProfile() {
        return profile;
    }

    public void setProfile(CustomNpcStyleProfileDto profile) {
        this.profile = profile;
    }
}
