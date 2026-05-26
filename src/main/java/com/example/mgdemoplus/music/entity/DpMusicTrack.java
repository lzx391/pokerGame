package com.example.mgdemoplus.music.entity;

import java.time.LocalDateTime;

/**
 * 曲库元数据，对应表 {@code dp_music_track}。
 */
public class DpMusicTrack {
    private Long id;
    private String storedFilename;//磁盘文件名（建议含 uuid 前缀，避免重名）
    private String displayName;//界面展示标题
    private String webPath;//浏览器访问路径，如 /music/xxx.mp3
    private Integer sortOrder;//列表排序，越大越靠前或反之由业务约定
    private Boolean enabled;//1=上架 0=下架（不删文件）
    private Integer uploaderUserId;//可选：上传人 dp_user.id
    private LocalDateTime createdAt;//入库时间
    private LocalDateTime updatedAt;//最后修改时间

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getWebPath() {
        return webPath;
    }

    public void setWebPath(String webPath) {
        this.webPath = webPath;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(Integer uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
