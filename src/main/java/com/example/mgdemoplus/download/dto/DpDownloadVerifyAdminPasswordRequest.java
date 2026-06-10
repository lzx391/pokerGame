package com.example.mgdemoplus.download.dto;

/** {@code POST /dpDownload/verifyAdminPassword} 请求体。 */
public class DpDownloadVerifyAdminPasswordRequest {
    private String adminPassword;

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
