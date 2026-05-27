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
class DpUserServiceImplNicknameValidationTest {

    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpUserStatsMapper dpUserStatsMapper;
    @Mock
    private DpSensitiveWordService sensitiveWordService;

    @InjectMocks
    private DpUserServiceImpl service;

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

    @Test
    void updateProfile_numericNickname_rejected() {
        DpUser current = new DpUser();
        current.setId(1);
        current.setNickname("alice");

        DpUser stored = new DpUser();
        stored.setId(1);
        stored.setNickname("alice");
        stored.setPassword(CryptoUtil.bcryptEncode("oldpass"));

        when(dpUserMapper.selectById(1)).thenReturn(stored);

        DpUserProfileUpdateRequest request = new DpUserProfileUpdateRequest();
        request.setOldPassword("oldpass");
        request.setNickname("99");

        DpUserProfileUpdateResult result = service.updateProfile(current, request);

        assertThat(result.getMessage()).isEqualTo(DpUserServiceImpl.MSG_NUMERIC_NICKNAME);
        verify(dpUserMapper, never()).updateNickname(any());
    }
}
