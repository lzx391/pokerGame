package com.example.mgdemoplus.oauth.dto;

public class OAuthResult {
    private boolean isNewUser;
    private boolean needSetupNickname;
    private String nickname;
    private int userId;

    public static OAuthResult existingUser(String nickname, int userId) {
        OAuthResult r = new OAuthResult();
        r.isNewUser = false;
        r.needSetupNickname = false;
        r.nickname = nickname;
        r.userId = userId;
        return r;
    }

    public static OAuthResult newUser(String nickname, int userId, boolean needSetupNickname) {
        OAuthResult r = new OAuthResult();
        r.isNewUser = true;
        r.needSetupNickname = needSetupNickname;
        r.nickname = nickname;
        r.userId = userId;
        return r;
    }

    public boolean isNewUser() { return isNewUser; }
    public boolean isNeedSetupNickname() { return needSetupNickname; }
    public String getNickname() { return nickname; }
    public int getUserId() { return userId; }
}
