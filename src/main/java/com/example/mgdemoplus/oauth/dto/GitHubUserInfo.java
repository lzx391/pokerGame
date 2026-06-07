package com.example.mgdemoplus.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GitHubUserInfo {
    private String login;
    private Long id;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    private String name;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
