package com.example.mgdemoplus.oauth.dto;

public class OAuthTokenResponse {
    private final String accessToken;

    public OAuthTokenResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public boolean isSuccess() {
        return accessToken != null && !accessToken.isBlank();
    }
}
