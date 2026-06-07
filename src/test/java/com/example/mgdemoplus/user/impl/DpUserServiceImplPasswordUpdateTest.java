package com.example.mgdemoplus.user.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.user.dto.DpUserPasswordUpdateRequest;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.utils.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpUserServiceImplPasswordUpdateTest {

    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpUserStatsMapper dpUserStatsMapper;
    @Mock
    private DpSensitiveWordService sensitiveWordService;

    @InjectMocks
    private DpUserServiceImpl service;

    private DpUser currentUser(int id, String nickname) {
        DpUser current = new DpUser();
        current.setId(id);
        current.setNickname(nickname);
        return current;
    }

    private DpUser storedUser(int id, String nickname, String passwordHashOrNull) {
        DpUser stored = new DpUser();
        stored.setId(id);
        stored.setNickname(nickname);
        stored.setPassword(passwordHashOrNull);
        return stored;
    }

    @Test
    void updatePassword_requiresNewPassword() {
        DpUser current = currentUser(1, "alice");

        String message = service.updatePassword(current, new DpUserPasswordUpdateRequest());

        assertThat(message).isEqualTo("请填写新密码");
        verify(dpUserMapper, never()).updatePasswordHash(any());
    }

    @Test
    void updatePassword_registeredUser_requiresOldPassword() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("oldpass"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);

        DpUserPasswordUpdateRequest request = new DpUserPasswordUpdateRequest();
        request.setNewPassword("newpass6");

        String message = service.updatePassword(current, request);

        assertThat(message).isEqualTo("请填写当前密码");
        verify(dpUserMapper, never()).updatePasswordHash(any());
    }

    @Test
    void updatePassword_wrongOldPassword_rejected() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("oldpass"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);

        DpUserPasswordUpdateRequest request = new DpUserPasswordUpdateRequest();
        request.setNewPassword("newpass6");
        request.setOldPassword("wrong");

        String message = service.updatePassword(current, request);

        assertThat(message).isEqualTo("当前密码错误");
        verify(dpUserMapper, never()).updatePasswordHash(any());
    }

    @Test
    void updatePassword_registeredUser_success() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("oldpass"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);
        when(dpUserMapper.updatePasswordHash(any())).thenReturn(1);

        DpUserPasswordUpdateRequest request = new DpUserPasswordUpdateRequest();
        request.setNewPassword("newpass6");
        request.setOldPassword("oldpass");

        String message = service.updatePassword(current, request);

        assertThat(message).isEqualTo("保存成功");
        verify(dpUserMapper).updatePasswordHash(any());
        verify(dpUserMapper, never()).updateNickname(any());
    }

    @Test
    void updatePassword_oauthFirstPassword_noOldPasswordRequired() {
        DpUser current = currentUser(3, "gh_user");
        DpUser stored = storedUser(3, "gh_user", null);
        when(dpUserMapper.selectById(3)).thenReturn(stored);
        when(dpUserMapper.updatePasswordHash(any())).thenReturn(1);

        DpUserPasswordUpdateRequest request = new DpUserPasswordUpdateRequest();
        request.setNewPassword("firstpass6");

        String message = service.updatePassword(current, request);

        assertThat(message).isEqualTo("保存成功");
        verify(dpUserMapper).updatePasswordHash(any());
    }

    @Test
    void updatePassword_oauthUser_oldPasswordRejected() {
        DpUser current = currentUser(3, "gh_user");
        DpUser stored = storedUser(3, "gh_user", null);
        when(dpUserMapper.selectById(3)).thenReturn(stored);

        DpUserPasswordUpdateRequest request = new DpUserPasswordUpdateRequest();
        request.setNewPassword("firstpass6");
        request.setOldPassword("should-not-send");

        String message = service.updatePassword(current, request);

        assertThat(message).isEqualTo("尚未设置密码，无需填写当前密码");
        verify(dpUserMapper, never()).updatePasswordHash(any());
    }

    @Test
    void updatePassword_tooShort_rejected() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("oldpass"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);

        DpUserPasswordUpdateRequest request = new DpUserPasswordUpdateRequest();
        request.setNewPassword("short");
        request.setOldPassword("oldpass");

        String message = service.updatePassword(current, request);

        assertThat(message).isEqualTo("新密码至少 6 位");
        verify(dpUserMapper, never()).updatePasswordHash(any());
    }
}
