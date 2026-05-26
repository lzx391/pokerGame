package com.example.mgdemoplus.user.dto;

/**
 * 头像上传结果（由 {@link com.example.mgdemoplus.user.DpUserService#uploadAvatar} 返回）。
 */
public class DpAvatarUploadResult {

    private final boolean success;
    private final String message;
    private final String avatarUrl;
    private final Long avatarUpdatedAt;

    private DpAvatarUploadResult(boolean success, String message, String avatarUrl, Long avatarUpdatedAt) {
        this.success = success;
        this.message = message;
        this.avatarUrl = avatarUrl;
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    public static DpAvatarUploadResult ok(String avatarUrl, Long avatarUpdatedAt) {
        return new DpAvatarUploadResult(true, "上传成功", avatarUrl, avatarUpdatedAt);
    }

    public static DpAvatarUploadResult fail(String message) {
        return new DpAvatarUploadResult(false, message, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Long getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }
}
