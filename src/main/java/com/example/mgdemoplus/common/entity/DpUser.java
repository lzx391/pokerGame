package com.example.mgdemoplus.common.entity;

import java.time.LocalDateTime;

public class DpUser {
    private int id;
    private String nickname;
    private String password;
    private String avatarUrl;
    private LocalDateTime avatarUpdatedAt;

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", nickname='" + nickname + '\'' +
                ", password='" + password + '\'' +
                '}';
    }

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public LocalDateTime getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(LocalDateTime avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }
}
