package com.example.mgdemoplus.download.dto;

/** {@code POST /dpDownload/delete} 请求体。 */
public class DpDownloadDeleteRequest {
    private Long id;
    private String adminPassword;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
