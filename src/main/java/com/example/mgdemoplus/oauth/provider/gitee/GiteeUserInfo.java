package com.example.mgdemoplus.oauth.provider.gitee;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GiteeUserInfo {
    private String login;
    private Long id;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    private String name;

    String getLogin() { return login; }
    void setLogin(String login) { this.login = login; }
    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getAvatarUrl() { return avatarUrl; }
    void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
}
