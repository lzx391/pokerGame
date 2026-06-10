package com.example.mgdemoplus.room.dto;

/** {@code POST /dpRoom/verifyExperimentalDeckPassword} 请求体。 */
public class VerifyExperimentalDeckPasswordRequest {
    private String roomId;
    private String experimentalPassword;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getExperimentalPassword() {
        return experimentalPassword;
    }

    public void setExperimentalPassword(String experimentalPassword) {
        this.experimentalPassword = experimentalPassword;
    }
}
