package com.example.mgdemoplus.user.dto;

/**
 * 资料更新结果；昵称变更时会下发新 token（JWT subject 为昵称）。
 */
public class DpUserProfileUpdateResult {

    private String message;
    private boolean nicknameChanged;
    private String token;
    private String nickname;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isNicknameChanged() {
        return nicknameChanged;
    }

    public void setNicknameChanged(boolean nicknameChanged) {
        this.nicknameChanged = nicknameChanged;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
