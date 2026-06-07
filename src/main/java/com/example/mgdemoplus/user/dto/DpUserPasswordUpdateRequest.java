package com.example.mgdemoplus.user.dto;

/**
 * 修改或首次设置登录密码（{@code PUT /dpUser/password}）：
 * <ul>
 *   <li>已设密用户：{@code newPassword} + {@code oldPassword}</li>
 *   <li>OAuth 等无密码用户：仅 {@code newPassword}</li>
 * </ul>
 */
public class DpUserPasswordUpdateRequest {

    private String oldPassword;
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
