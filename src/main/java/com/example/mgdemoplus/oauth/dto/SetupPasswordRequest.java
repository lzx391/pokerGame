package com.example.mgdemoplus.oauth.dto;

public class SetupPasswordRequest {
    private String setupToken;
    private String newPassword;

    public String getSetupToken() { return setupToken; }
    public void setSetupToken(String setupToken) { this.setupToken = setupToken; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
