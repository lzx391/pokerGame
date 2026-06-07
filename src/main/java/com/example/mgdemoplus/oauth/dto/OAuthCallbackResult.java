package com.example.mgdemoplus.oauth.dto;

public class OAuthCallbackResult {
    private boolean success;
    private String message;
    private boolean isNewUser;
    private boolean needSetupNickname;
    private String nickname;
    private int userId;
    private String setupToken;

    public static OAuthCallbackResult fail(String message) {
        OAuthCallbackResult r = new OAuthCallbackResult();
        r.success = false;
        r.message = message;
        return r;
    }

    public static OAuthCallbackResult loginSuccess(String nickname, int userId, boolean isNewUser, boolean needSetupNickname) {
        OAuthCallbackResult r = new OAuthCallbackResult();
        r.success = true;
        r.nickname = nickname;
        r.userId = userId;
        r.isNewUser = isNewUser;
        r.needSetupNickname = needSetupNickname;
        return r;
    }

    public static OAuthCallbackResult setupToken(String setupToken) {
        OAuthCallbackResult r = new OAuthCallbackResult();
        r.success = true;
        r.setupToken = setupToken;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public boolean isNewUser() { return isNewUser; }
    public boolean isNeedSetupNickname() { return needSetupNickname; }
    public String getNickname() { return nickname; }
    public int getUserId() { return userId; }
    public String getSetupToken() { return setupToken; }
}
