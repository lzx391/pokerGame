package com.example.mgdemoplus.oauth.dto;

public class OAuthTokenResponse {
    private final String accessToken;
    private final String unionId;
    private final String openId;

    public OAuthTokenResponse(String accessToken) {
        this(accessToken, null, null);
    }

    public OAuthTokenResponse(String accessToken, String unionId, String openId) {
        this.accessToken = accessToken;
        this.unionId = unionId;
        this.openId = openId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUnionId() {
        return unionId;
    }

    public String getOpenId() {
        return openId;
    }

    public boolean isSuccess() {
        return accessToken != null && !accessToken.isBlank();
    }
}
