package com.example.mgdemoplus.oauth.dto;

public class OAuthUserProfile {
    private final String openId;
    private final String avatarUrl;
    private final String displayName;

    public OAuthUserProfile(String openId, String avatarUrl, String displayName) {
        this.openId = openId;
        this.avatarUrl = avatarUrl;
        this.displayName = displayName;
    }

    public String getOpenId() {
        return openId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getDisplayName() {
        return displayName;
    }
}
