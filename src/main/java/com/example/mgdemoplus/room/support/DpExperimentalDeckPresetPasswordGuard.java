package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 实验排牌功能访问密码校验；密码仅来自环境变量 / application.yml，不落库。
 */
@Component
public class DpExperimentalDeckPresetPasswordGuard {

    private final String configuredPassword;

    public DpExperimentalDeckPresetPasswordGuard(
            @Value("${mgdemoplus.experimental-deck-preset-password:}") String configuredPassword) {
        this.configuredPassword = configuredPassword != null ? configuredPassword.trim() : "";
    }

    public boolean isEnabled() {
        return !configuredPassword.isEmpty();
    }

    public boolean verify(String submitted) {
        if (!isEnabled() || submitted == null) {
            return false;
        }
        byte[] expected = configuredPassword.getBytes(StandardCharsets.UTF_8);
        byte[] actual = submitted.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public ResultUtil rejectNotConfigured() {
        return ResultUtil.error().data("message", "实验排牌功能未启用，请联系管理员配置访问密码");
    }

    public ResultUtil rejectWrongPassword() {
        return ResultUtil.error().data("message", "实验排牌访问密码错误");
    }

    public ResultUtil rejectMissingPassword() {
        return ResultUtil.error().data("message", "请先验证实验排牌访问密码");
    }

    public ResultUtil gate(String submitted) {
        if (!isEnabled()) {
            return rejectNotConfigured();
        }
        if (submitted == null || submitted.isBlank()) {
            return rejectMissingPassword();
        }
        if (!verify(submitted)) {
            return rejectWrongPassword();
        }
        return null;
    }
}
