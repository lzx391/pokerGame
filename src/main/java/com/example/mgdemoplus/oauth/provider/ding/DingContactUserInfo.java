package com.example.mgdemoplus.oauth.provider.ding;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 钉钉 OAuth2 {@code GET /v1.0/contact/users/me} 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DingContactUserInfo {

    private String nick;
    @JsonProperty("avatarUrl")
    @JsonAlias("avatar_url")
    private String avatarUrl;
    @JsonProperty("openId")
    @JsonAlias({"open_id", "openid"})
    private String openId;
    @JsonProperty("unionId")
    @JsonAlias({"union_id", "unionid"})
    private String unionId;

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    public String getUnionId() {
        return unionId;
    }

    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }
}
