package com.example.mgdemoplus.security;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 从 {@link SecurityContextHolder} 解析当前登录用户（JWT subject = 昵称 → {@code dp_user}）。
 */
@Component
public class DpCurrentUserSupport {

    @Autowired
    private DpUserMapper dpUserMapper;

    public Optional<String> resolveNicknameOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return Optional.empty();
        }
        String nickname = auth.getName();
        if (nickname == null || nickname.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(nickname.trim());
    }

    public Optional<DpUser> resolveUserOptional() {
        return resolveNicknameOptional()
                .map(dpUserMapper::selectByNickname)
                .filter(u -> u != null);
    }

    /** 未登录或用户不存在时返回 {@code null}。 */
    public String requireNickname() {
        return resolveNicknameOptional().orElse(null);
    }

    /** 未登录或用户不存在时返回 {@code null}。 */
    public DpUser requireUser() {
        return resolveUserOptional().orElse(null);
    }

    /** 未登录或用户不存在时返回 {@code null}。 */
    public Integer requireUserId() {
        return resolveUserOptional().map(DpUser::getId).orElse(null);
    }

    /**
     * Social / Mailbox 风格：失败时写入 {@code fallback} 并返回 {@code null}。
     */
    public DpUser requireUser(ResultUtil fallback) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            fallback.setSuccess(false);
            fallback.setMessage("未登录或登录已失效");
            return null;
        }
        String nickname = auth.getName();
        DpUser u = dpUserMapper.selectByNickname(nickname);
        if (u == null) {
            fallback.setSuccess(false);
            fallback.setMessage("用户不存在或未同步");
            return null;
        }
        return u;
    }

    /**
     * {@code getNowRoom} 等旁观接口：已登录返回 JWT 昵称，否则返回 {@code fallback}（可为 {@code null}）。
     */
    public String resolveViewerNicknameOr(String fallback) {
        return resolveNicknameOptional().orElse(fallback);
    }
}
