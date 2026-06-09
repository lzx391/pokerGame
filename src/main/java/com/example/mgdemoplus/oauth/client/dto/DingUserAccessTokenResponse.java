package com.example.mgdemoplus.oauth.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 钉钉 OAuth2 {@code POST /v1.0/oauth2/userAccessToken} 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DingUserAccessTokenResponse {

    @JsonProperty("accessToken")
    @JsonAlias("access_token")
    private String accessToken;
    @JsonProperty("refreshToken")
    @JsonAlias("refresh_token")
    private String refreshToken;
    @JsonProperty("expireIn")
    @JsonAlias({"expires_in", "expire_in"})
    private Long expireIn;
    @JsonProperty("corpId")
    @JsonAlias("corp_id")
    private String corpId;
    @JsonProperty("unionId")
    @JsonAlias({"union_id", "unionid"})
    private String unionId;
    @JsonProperty("openId")
    @JsonAlias({"open_id", "openid"})
    private String openId;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpireIn() {
        return expireIn;
    }

    public void setExpireIn(Long expireIn) {
        this.expireIn = expireIn;
    }

    public String getCorpId() {
        return corpId;
    }

    public void setCorpId(String corpId) {
        this.corpId = corpId;
    }

    public String getUnionId() {
        return unionId;
    }

    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }

    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }
}
