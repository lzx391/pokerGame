package com.example.mgdemoplus.user.dto;

/**
 * 头像上传结果（由 {@link com.example.mgdemoplus.user.DpUserService#uploadAvatar} 返回）。
 */
public class DpAvatarUploadResult {

    private final boolean success;
    private final String message;
    private final String avatarUrl;

    private DpAvatarUploadResult(boolean success, String message, String avatarUrl) {
        this.success = success;
        this.message = message;
        this.avatarUrl = avatarUrl;
    }

    public static DpAvatarUploadResult ok(String avatarUrl) {
        return new DpAvatarUploadResult(true, "上传成功", avatarUrl);
    }

    public static DpAvatarUploadResult fail(String message) {
        return new DpAvatarUploadResult(false, message, null);
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
}
