package com.example.mgdemoplus.security;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DpCurrentUserSupportTest {

    private final DpUserMapper dpUserMapper = mock(DpUserMapper.class);
    private final DpCurrentUserSupport support = new DpCurrentUserSupport();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveNicknameOptional_whenAnonymous_empty() {
        injectMapper();
        assertThat(support.resolveNicknameOptional()).isEmpty();
        assertThat(support.requireNickname()).isNull();
    }

    @Test
    void requireUser_whenAuthenticated_returnsUser() {
        injectMapper();
        DpUser u = new DpUser();
        u.setId(7);
        u.setNickname("Alice");
        when(dpUserMapper.selectByNickname("Alice")).thenReturn(u);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("Alice", null, null));

        assertThat(support.requireUser()).isSameAs(u);
        assertThat(support.requireUserId()).isEqualTo(7);
        assertThat(support.resolveViewerNicknameOr("fallback")).isEqualTo("Alice");
    }

    @Test
    void requireUser_withFallback_setsMessageWhenMissing() {
        injectMapper();
        ResultUtil err = ResultUtil.error();
        assertThat(support.requireUser(err)).isNull();
        assertThat(err.getSuccess()).isFalse();
        assertThat(err.getMessage()).contains("未登录");
    }

    @Test
    void resolveViewerNicknameOr_whenAnonymous_returnsFallback() {
        injectMapper();
        assertThat(support.resolveViewerNicknameOr("anon")).isEqualTo("anon");
        assertThat(support.resolveViewerNicknameOr(null)).isNull();
    }

    private void injectMapper() {
        try {
            var f = DpCurrentUserSupport.class.getDeclaredField("dpUserMapper");
            f.setAccessible(true);
            f.set(support, dpUserMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
