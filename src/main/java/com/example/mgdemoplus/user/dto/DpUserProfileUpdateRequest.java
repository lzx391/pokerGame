package com.example.mgdemoplus.user.dto;

/**
 * 修改非敏感个人资料（{@code PUT /dpUser/profile}），当前仅支持 {@code nickname}。
 * 改密请使用 {@code PUT /dpUser/password}。
 */
public class DpUserProfileUpdateRequest {

    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
