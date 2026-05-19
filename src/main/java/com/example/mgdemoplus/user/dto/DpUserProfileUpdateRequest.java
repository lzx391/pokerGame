package com.example.mgdemoplus.user.dto;

/**
 * 修改个人资料：保存时须带当前密码；新密码可选（空表示不改密码）。
 */
public class DpUserProfileUpdateRequest {

    private String nickname;
    private String oldPassword;
    /** 为空或空白表示不修改密码 */
    private String newPassword;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
