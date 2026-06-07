package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.oauth.dto.SetupPasswordRequest;
import com.example.mgdemoplus.oauth.impl.DpOAuthService;
import com.example.mgdemoplus.oauth.dto.OAuthCallbackResult;
import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.user.cache.DpRedisLoginCacheService;
import com.example.mgdemoplus.utils.CryptoUtil;
import com.example.mgdemoplus.utils.ResultUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/oauth")
public class DpOAuthController {

    @Autowired
    private DpOAuthService dpOAuthService;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private DpRedisLoginCacheService dpRedisLoginCacheService;
    @Autowired
    private DpUserMapper dpUserMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mgdemoplus.oauth.github.frontend-base-url:http://localhost:8080}")
    private String frontendBaseUrl;

    private static final long EXCHANGE_TOKEN_TTL_SECONDS = 120;
    private static final String REDIS_EXCHANGE_PREFIX = "oauth:exchange:";

    // ==================== 授权 URL ====================

    @GetMapping("/github/authorize-url")
    public ResultUtil getAuthorizeUrl(@RequestParam(required = false) String mode) {
        Integer userId = null;
        if ("set-password".equals(mode)) {
            DpUser current = requireCurrentUser();
            if (current == null) {
                return ResultUtil.error().data("message", "请先登录");
            }
            userId = current.getId();
        }
        String url = dpOAuthService.buildAuthorizeUrl(mode, userId);
        return ResultUtil.ok().data("url", url);
    }

    // ==================== GitHub 回调（GET：浏览器从 GitHub 跳回后端的入口） ====================

    @GetMapping("/github/callback")
    public void githubCallback(@RequestParam String code,
                               @RequestParam(required = false) String state,
                               HttpServletResponse response) throws IOException {
        if (code == null || code.isBlank()) {
            redirectToFrontend(response, "error", "缺少授权码");
            return;
        }

        OAuthCallbackResult result = dpOAuthService.handleCallback(code, state);
        if (!result.isSuccess()) {
            redirectToFrontend(response, "error", result.getMessage());
            return;
        }

        // set-password 模式
        if (result.getSetupToken() != null) {
            redirectToFrontend(response, "setup-password", null,
                    Map.of("setupToken", result.getSetupToken()));
            return;
        }

        // 登录模式：生成 JWT，存到 Redis 的 exchange token 里
        String jti = UUID.randomUUID().toString();
        String token = jwtTokenService.generateToken(result.getNickname(), jti);
        dpRedisLoginCacheService.setLoginJti(result.getNickname(), jti);

        Map<String, Object> loginData = new LinkedHashMap<>();
        loginData.put("token", token);
        loginData.put("nickname", result.getNickname());
        loginData.put("userId", result.getUserId());
        loginData.put("isNewUser", result.isNewUser());
        loginData.put("needSetupNickname", result.isNeedSetupNickname());

        redirectToFrontend(response, "login", null, loginData);
    }

    // ==================== 前端换 token ====================

    @PostMapping("/exchange-token")
    public ResultUtil exchangeToken(@RequestBody Map<String, String> body) {
        String oid = body.get("oid");
        if (oid == null || oid.isBlank()) {
            return ResultUtil.error().data("message", "缺少交换令牌");
        }
        String json = stringRedisTemplate.opsForValue().get(REDIS_EXCHANGE_PREFIX + oid);
        if (json == null) {
            return ResultUtil.error().data("message", "交换令牌无效或已过期");
        }
        stringRedisTemplate.delete(REDIS_EXCHANGE_PREFIX + oid);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            ResultUtil ok = ResultUtil.ok();
            data.forEach(ok::data);
            return ok;
        } catch (Exception e) {
            return ResultUtil.error().data("message", "数据解析失败");
        }
    }

    // ==================== 设置密码 ====================

    @PostMapping("/setup-password")
    public ResultUtil setupPassword(@RequestBody SetupPasswordRequest request) {
        if (request.getSetupToken() == null || request.getSetupToken().isBlank()) {
            return ResultUtil.error().data("message", "缺少验证令牌");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return ResultUtil.error().data("message", "新密码至少 6 位");
        }

        String userIdStr = dpOAuthService.consumeSetupToken(request.getSetupToken());
        if (userIdStr == null) {
            return ResultUtil.error().data("message", "验证令牌无效或已过期，请重新授权");
        }

        int userId = Integer.parseInt(userIdStr);
        DpUser user = dpUserMapper.selectById(userId);
        if (user == null) {
            return ResultUtil.error().data("message", "用户不存在");
        }

        DpUser patch = new DpUser();
        patch.setId(userId);
        patch.setPassword(CryptoUtil.bcryptEncode(request.getNewPassword()));
        dpUserMapper.updatePasswordHash(patch);

        return ResultUtil.ok().data("message", "密码设置成功");
    }

    // ==================== 内部工具 ====================

    private void redirectToFrontend(HttpServletResponse response, String mode, String error) throws IOException {
        redirectToFrontend(response, mode, error, null);
    }

    private void redirectToFrontend(HttpServletResponse response, String mode, String error,
                                     Map<String, Object> data) throws IOException {
        String oid = null;
        if (data != null && !data.isEmpty()) {
            oid = UUID.randomUUID().toString().replace("-", "");
            try {
                String json = objectMapper.writeValueAsString(data);
                stringRedisTemplate.opsForValue().set(REDIS_EXCHANGE_PREFIX + oid, json, EXCHANGE_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                oid = null;
            }
        }

        StringBuilder url = new StringBuilder(frontendBaseUrl);
        url.append("/#/oauth/callback?mode=").append(mode);
        if (error != null) {
            url.append("&error=").append(java.net.URLEncoder.encode(error, "UTF-8"));
        }
        if (oid != null) {
            url.append("&oid=").append(oid);
        }

        response.sendRedirect(url.toString());
    }

    private DpUser requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return null;
        }
        return dpUserMapper.selectByNickname(auth.getName());
    }
}
