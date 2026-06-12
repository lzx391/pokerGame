package com.example.mgdemoplus.user.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
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
class DpUserServiceImplProfileUpdateTest {

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
    void register_numericNickname_rejected() {
        DpUser incoming = new DpUser();
        incoming.setNickname("12345");
        incoming.setPassword("secret1");

        int code = service.registerUser(incoming);

        assertThat(code).isEqualTo(DpUserServiceImpl.REGISTER_NUMERIC_NICKNAME);
        verify(dpUserMapper, never()).registerUser(any());
    }

    @Test
    void register_alphanumericNickname_notRejectedByNumericRule() {
        DpUser incoming = new DpUser();
        incoming.setNickname("abc12");
        incoming.setPassword("secret1");
        when(sensitiveWordService.containsSensitive("abc12")).thenReturn(false);
        when(dpUserMapper.selectByNickname("abc12")).thenReturn(null);
        when(dpUserMapper.registerUser(any())).thenReturn(DpUserServiceImpl.REGISTER_OK);

        int code = service.registerUser(incoming);

        assertThat(code).isEqualTo(DpUserServiceImpl.REGISTER_OK);
    }

    // --- 场景 A：仅改昵称 ---

    @Test
    void updateProfile_nicknameOnly_registeredUser_noOldPasswordRequired() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("secret1"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);
        when(sensitiveWordService.containsSensitive("bob")).thenReturn(false);
        when(dpUserMapper.selectByNickname("bob")).thenReturn(null);
        when(dpUserMapper.updateNickname(any())).thenReturn(1);

        DpUserProfileUpdateRequest request = new DpUserProfileUpdateRequest();
        request.setNickname("bob");

        DpUserProfileUpdateResult result = service.updateProfile(current, request);

        assertThat(result.getMessage()).isEqualTo("保存成功");
        assertThat(result.isNicknameChanged()).isTrue();
        assertThat(result.getNickname()).isEqualTo("bob");
        verify(dpUserMapper, never()).updatePasswordHash(any());
    }

    @Test
    void updateProfile_nicknameOnly_oauthUser_noOldPasswordRequired() {
        DpUser current = currentUser(2, "gh_user");
        DpUser stored = storedUser(2, "gh_user", null);
        when(dpUserMapper.selectById(2)).thenReturn(stored);
        when(sensitiveWordService.containsSensitive("gh_new")).thenReturn(false);
        when(dpUserMapper.selectByNickname("gh_new")).thenReturn(null);
        when(dpUserMapper.updateNickname(any())).thenReturn(1);

        DpUserProfileUpdateRequest request = new DpUserProfileUpdateRequest();
        request.setNickname("gh_new");

        DpUserProfileUpdateResult result = service.updateProfile(current, request);

        assertThat(result.getMessage()).isEqualTo("保存成功");
        assertThat(result.isNicknameChanged()).isTrue();
    }

    @Test
    void updateProfile_sameNickname_noChange() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("secret1"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);

        DpUserProfileUpdateRequest request = new DpUserProfileUpdateRequest();
        request.setNickname("alice");

        DpUserProfileUpdateResult result = service.updateProfile(current, request);

        assertThat(result.getMessage()).isEqualTo("没有需要保存的修改");
        verify(dpUserMapper, never()).updateNickname(any());
    }

    @Test
    void updateProfile_numericNickname_rejected() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("oldpass"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);

        DpUserProfileUpdateRequest request = new DpUserProfileUpdateRequest();
        request.setNickname("99");

        DpUserProfileUpdateResult result = service.updateProfile(current, request);

        assertThat(result.getMessage()).isEqualTo(DpUserServiceImpl.MSG_NUMERIC_NICKNAME);
        verify(dpUserMapper, never()).updateNickname(any());
    }

    @Test
    void updateProfile_emptyRequest_rejected() {
        DpUser current = currentUser(1, "alice");
        DpUser stored = storedUser(1, "alice", CryptoUtil.bcryptEncode("oldpass"));
        when(dpUserMapper.selectById(1)).thenReturn(stored);

        DpUserProfileUpdateResult result = service.updateProfile(current, new DpUserProfileUpdateRequest());

        assertThat(result.getMessage()).isEqualTo("没有需要保存的修改");
    }
}
