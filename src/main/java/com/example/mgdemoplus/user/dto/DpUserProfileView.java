package com.example.mgdemoplus.user.dto;

/**
 * 个人资料只读视图：不含密码哈希。
 */
public class DpUserProfileView {

    private int id;
    private String nickname;
    private String avatarUrl;
    /** 是否已设置登录密码（库内非空） */
    private boolean passwordSet;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public void setPasswordSet(boolean passwordSet) {
        this.passwordSet = passwordSet;
    }
}
