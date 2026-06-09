package com.example.mgdemoplus.oauth.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.oauth.dto.OAuthCallbackResult;
import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.mapper.DpSocialAuthMapper;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.example.mgdemoplus.oauth.provider.DpOAuthProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpOAuthServiceNicknameTest {

    @Mock
    private DpSocialAuthMapper socialAuthMapper;
    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpSensitiveWordService sensitiveWordService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private DpOAuthProvider provider;

    private DpOAuthService service;

    @BeforeEach
    void setUp() {
        when(provider.id()).thenReturn("gitee");
        when(provider.enabled()).thenReturn(true);
        DpOAuthProviderRegistry registry = new DpOAuthProviderRegistry(List.of(provider));
        service = new DpOAuthService(
                "file:/tmp/",
                socialAuthMapper,
                dpUserMapper,
                sensitiveWordService,
                stringRedisTemplate,
                new ObjectMapper(),
                registry);
    }

    @Test
    void handleCallback_sensitiveDisplayName_usesFallbackWithinTenChars() {
        when(stringRedisTemplate.execute(any(), eq(List.of("oauth:state:state-1"))))
                .thenReturn("{\"mode\":\"login\",\"provider\":\"gitee\"}");
        when(provider.exchangeCode("code")).thenReturn(new OAuthTokenResponse("token"));
        when(provider.fetchUserProfile(any(OAuthTokenResponse.class)))
                .thenReturn(new OAuthUserProfile("gitee_login", null, "敏感昵称"));
        when(socialAuthMapper.selectByProviderAndOpenId("gitee", "gitee_login")).thenReturn(null);
        when(sensitiveWordService.containsSensitive("敏感昵称")).thenReturn(true);
        when(dpUserMapper.selectByNickname(any())).thenReturn(null);
        when(dpUserMapper.registerUser(any())).thenAnswer(invocation -> {
            DpUser user = invocation.getArgument(0);
            user.setId(42);
            return 1;
        });

        OAuthCallbackResult result = service.handleCallback("gitee", "code", "state-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isNeedSetupNickname()).isTrue();
        ArgumentCaptor<DpUser> userCaptor = ArgumentCaptor.forClass(DpUser.class);
        verify(dpUserMapper).registerUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).hasSizeLessThanOrEqualTo(10);
        assertThat(userCaptor.getValue().getNickname()).startsWith("p");
    }
}
