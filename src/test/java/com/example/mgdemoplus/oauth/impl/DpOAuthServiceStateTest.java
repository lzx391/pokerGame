package com.example.mgdemoplus.oauth.impl;

import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.oauth.dto.OAuthCallbackResult;
import com.example.mgdemoplus.oauth.mapper.DpSocialAuthMapper;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.example.mgdemoplus.oauth.provider.DpOAuthProviderRegistry;
import com.example.mgdemoplus.storage.DpAvatarStorageSupport;
import com.example.mgdemoplus.storage.DpObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpOAuthServiceStateTest {

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
    @Mock
    private DpObjectStorage objectStorage;
    @Mock
    private DpAvatarStorageSupport avatarStorageSupport;

    private DpOAuthService service;

    @BeforeEach
    void setUp() {
        when(provider.id()).thenReturn("github");
        when(provider.enabled()).thenReturn(true);
        DpOAuthProviderRegistry registry = new DpOAuthProviderRegistry(List.of(provider));
        service = new DpOAuthService(
                objectStorage,
                avatarStorageSupport,
                socialAuthMapper,
                dpUserMapper,
                sensitiveWordService,
                stringRedisTemplate,
                new ObjectMapper(),
                registry);
    }

    @Test
    void handleCallback_blankState_rejectsWithoutRedis() {
        OAuthCallbackResult result = service.handleCallback("github", "code", "  ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("无效");
        verify(stringRedisTemplate, never()).execute(any(RedisScript.class), any());
    }

    @Test
    void handleCallback_missingState_rejects() {
        when(stringRedisTemplate.execute(any(RedisScript.class), any())).thenReturn(null);

        OAuthCallbackResult result = service.handleCallback("github", "code", "state-uuid");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("无效");
    }

    @Test
    void handleCallback_providerMismatch_rejectsAfterAtomicConsume() {
        when(stringRedisTemplate.execute(any(RedisScript.class), any()))
                .thenReturn("{\"mode\":\"login\",\"provider\":\"other\"}");

        OAuthCallbackResult result = service.handleCallback("github", "code", "state-uuid");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("不匹配");
        verify(stringRedisTemplate).execute(any(RedisScript.class), eq(List.of("oauth:state:state-uuid")));
    }

    @Test
    void consumeState_usesLuaGetDelScriptForOldRedisCompatibility() {
        when(stringRedisTemplate.execute(any(RedisScript.class), any())).thenReturn(null);

        service.handleCallback("github", "code", "abc");

        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(stringRedisTemplate).execute(scriptCaptor.capture(), eq(List.of("oauth:state:abc")));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains("redis.call('GET'");
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains("redis.call('DEL'");
    }
}
