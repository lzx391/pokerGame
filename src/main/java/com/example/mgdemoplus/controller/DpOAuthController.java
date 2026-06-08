package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.oauth.dto.OAuthCallbackResult;
import com.example.mgdemoplus.oauth.impl.DpOAuthService;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.example.mgdemoplus.oauth.provider.DpOAuthProviderRegistry;
import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.user.cache.DpRedisLoginCacheService;
import com.example.mgdemoplus.utils.ResultUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/oauth")
public class DpOAuthController {

    @Autowired
    private DpOAuthService dpOAuthService;
    @Autowired
    private DpOAuthProviderRegistry providerRegistry;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private DpRedisLoginCacheService dpRedisLoginCacheService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mgdemoplus.oauth.github.frontend-base-url:http://localhost:8080}")
    private String frontendBaseUrl;

    private static final long EXCHANGE_TOKEN_TTL_SECONDS = 120;
    private static final String REDIS_EXCHANGE_PREFIX = "oauth:exchange:";

    @GetMapping("/providers")
    public ResultUtil listProviders() {
        List<Map<String, String>> providers = providerRegistry.enabledProviders().stream()
                .map(p -> Map.of("id", p.id(), "displayName", p.displayName()))
                .collect(Collectors.toList());
        return ResultUtil.ok().data("providers", providers);
    }

    @GetMapping("/{provider}/authorize-url")
    public ResultUtil getAuthorizeUrl(@PathVariable String provider) {
        try {
            String url = dpOAuthService.buildAuthorizeUrl(provider);
            return ResultUtil.ok().data("url", url);
        } catch (IllegalArgumentException e) {
            return ResultUtil.error().data("message", e.getMessage());
        } catch (IllegalStateException e) {
            return ResultUtil.error().data("message", e.getMessage());
        }
    }
/**
 * 拿着授权的code和state给后端取信息
 * @param provider
 * @param code
 * @param state
 * @param response
 * @throws IOException
 */
    @GetMapping("/{provider}/callback")
    public void oauthCallback(@PathVariable String provider,
                              @RequestParam String code,
                              @RequestParam(required = false) String state,
                              HttpServletResponse response) throws IOException {
        if (code == null || code.isBlank()) {
            redirectToFrontend(response, "error", "缺少授权码");
            return;
        }

        DpOAuthProvider oauthProvider = providerRegistry.get(provider);
        if (oauthProvider == null) {
            redirectToFrontend(response, "error", "未知 OAuth 提供商");
            return;
        }

        OAuthCallbackResult result = dpOAuthService.handleCallback(provider, code, state);
        if (!result.isSuccess()) {
            redirectToFrontend(response, "error", result.getMessage());
            return;
        }
        //登录成功的话，生成jti，签发token，存入redis
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
}
