package com.example.mgdemoplus.oauth.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.oauth.dto.GitHubUserInfo;
import com.example.mgdemoplus.oauth.dto.OAuthCallbackResult;
import com.example.mgdemoplus.oauth.entity.DpSocialAuth;
import com.example.mgdemoplus.oauth.mapper.DpSocialAuthMapper;
import com.example.mgdemoplus.utils.DpAvatarThumbnailSupport;
import com.example.mgdemoplus.utils.DpImageFileSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DpOAuthService {

    private static final Logger log = LoggerFactory.getLogger(DpOAuthService.class);
    private static final String PROVIDER_GITHUB = "github";
    private static final String REDIS_STATE_PREFIX = "oauth:state:";
    private static final String REDIS_SETUP_TOKEN_PREFIX = "oauth:setuppwd:";
    private static final long STATE_TTL_SECONDS = 600;
    private static final long SETUP_TOKEN_TTL_SECONDS = 300;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String imagesFileLocation;
    private final DpSocialAuthMapper socialAuthMapper;
    private final DpUserMapper dpUserMapper;
    private final DpSensitiveWordService sensitiveWordService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DpOAuthService(
            @Value("${mgdemoplus.oauth.github.client-id:}") String clientId,
            @Value("${mgdemoplus.oauth.github.client-secret:}") String clientSecret,
            @Value("${mgdemoplus.oauth.github.redirect-uri:}") String redirectUri,
            @Value("${mgdemoplus.images.file-location:file:P:/javaworkspace/DPGameFiles/images/}") String imagesFileLocation,
            DpSocialAuthMapper socialAuthMapper,
            DpUserMapper dpUserMapper,
            DpSensitiveWordService sensitiveWordService,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.imagesFileLocation = imagesFileLocation;
        this.socialAuthMapper = socialAuthMapper;
        this.dpUserMapper = dpUserMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    // ==================== 授权 URL ====================

    public String buildAuthorizeUrl(String mode, Integer userId) {
        String state = UUID.randomUUID().toString();

        if ("set-password".equals(mode) && userId != null) {
            storeState(state, Map.of("userId", userId, "mode", "set-password"));
        } else {
            storeState(state, Map.of("mode", "login"));
        }

        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&state=" + state
                + "&scope=read:user";
    }

    private void storeState(String state, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(REDIS_STATE_PREFIX + state, json, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("oauth state redis write failed", e);
        }
    }

    // ==================== 回调处理 ====================

    public OAuthCallbackResult handleCallback(String code, String receivedState) {
        StatePayload statePayload = consumeState(receivedState);
        String mode = statePayload != null ? statePayload.mode : "login";
        Integer stateUserId = statePayload != null ? statePayload.userId : null;

        String accessToken = exchangeCodeForToken(code);
        if (accessToken == null) {
            return OAuthCallbackResult.fail("GitHub 授权失败");
        }

        GitHubUserInfo ghUser = fetchGitHubUser(accessToken);
        if (ghUser == null || ghUser.getLogin() == null || ghUser.getLogin().isBlank()) {
            return OAuthCallbackResult.fail("获取 GitHub 用户信息失败");
        }

        if ("set-password".equals(mode) && stateUserId != null) {
            return handleSetPasswordMode(stateUserId);
        }
        return handleLoginMode(ghUser);
    }

    private OAuthCallbackResult handleLoginMode(GitHubUserInfo ghUser) {
        String githubLogin = ghUser.getLogin();

        DpSocialAuth existing = socialAuthMapper.selectByProviderAndOpenId(PROVIDER_GITHUB, githubLogin);
        if (existing != null) {
            DpUser user = dpUserMapper.selectById(existing.getUserId());
            if (user == null) {
                return OAuthCallbackResult.fail("用户数据异常，请联系管理员");
            }
            return OAuthCallbackResult.loginSuccess(user.getNickname(), user.getId(), false, false);
        }

        String defaultNickname = githubLogin;
        boolean needSetup = false;

        if (defaultNickname.length() > 10) {
            defaultNickname = defaultNickname.substring(0, 10);
        }
        if (defaultNickname.matches("\\d+") || sensitiveWordService.containsSensitive(defaultNickname)) {
            defaultNickname = "player_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            needSetup = true;
        }
        DpUser taken = dpUserMapper.selectByNickname(defaultNickname);
        if (taken != null) {
            defaultNickname = "player_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            needSetup = true;
        }

        DpUser newUser = new DpUser();
        newUser.setNickname(defaultNickname);
        newUser.setPassword(null);
        dpUserMapper.registerUser(newUser);

        DpSocialAuth auth = new DpSocialAuth();
        auth.setUserId(newUser.getId());
        auth.setProvider(PROVIDER_GITHUB);
        auth.setOpenId(githubLogin);
        socialAuthMapper.insert(auth);

        if (ghUser.getAvatarUrl() != null && !ghUser.getAvatarUrl().isBlank()) {
            String localPath = downloadAndStoreAvatar(newUser.getId(), ghUser.getAvatarUrl());
            if (localPath != null) {
                dpUserMapper.updateAvatarUrl(newUser.getId(), localPath, LocalDateTime.now());
            }
        }

        return OAuthCallbackResult.loginSuccess(defaultNickname, newUser.getId(), true, needSetup);
    }

    private OAuthCallbackResult handleSetPasswordMode(int userId) {
        String setupToken = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                REDIS_SETUP_TOKEN_PREFIX + setupToken,
                String.valueOf(userId),
                SETUP_TOKEN_TTL_SECONDS,
                TimeUnit.SECONDS);
        return OAuthCallbackResult.setupToken(setupToken);
    }

    public String consumeSetupToken(String setupToken) {
        String key = REDIS_SETUP_TOKEN_PREFIX + setupToken;
        String userIdStr = stringRedisTemplate.opsForValue().get(key);
        if (userIdStr != null) {
            stringRedisTemplate.delete(key);
        }
        return userIdStr;
    }

    // ==================== GitHub API ====================

    private String exchangeCodeForToken(String code) {
        try {
            String body = "client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&code=" + code
                    + "&redirect_uri=" + redirectUri;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://github.com/login/oauth/access_token"))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            log.info("github token exchange status={} body={}", response.statusCode(), respBody);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(respBody, Map.class);
            String accessToken = (String) map.get("access_token");
            if (accessToken == null) {
                log.error("github token exchange returned error: {}", respBody);
            }
            return accessToken;
        } catch (Exception e) {
            log.error("github token exchange failed", e);
            return null;
        }
    }

    private GitHubUserInfo fetchGitHubUser(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/user"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), GitHubUserInfo.class);
        } catch (Exception e) {
            log.error("github user fetch failed", e);
            return null;
        }
    }

    // ==================== 头像 ====================

    private String downloadAndStoreAvatar(int userId, String githubAvatarUrl) {
        try {
            String dir = DpImageFileSupport.toPhysicalDir(imagesFileLocation);
            File folder = new File(dir);
            if (!folder.exists() && !folder.mkdirs()) {
                return null;
            }
            DpImageFileSupport.deleteUserAvatarFiles(imagesFileLocation, userId);

            String storedFilename = userId + ".png";
            String webPath = "/images/" + storedFilename;

            URL url = URI.create(githubAvatarUrl).toURL();
            try (InputStream in = url.openStream()) {
                Files.copy(in, new File(folder, storedFilename).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            DpAvatarThumbnailSupport.writeThumbnailIfPossible(folder, userId, new File(folder, storedFilename));
            return webPath;
        } catch (Exception e) {
            log.error("avatar download failed userId={} url={}", userId, githubAvatarUrl, e);
            return null;
        }
    }

    // ==================== State ====================

    private StatePayload consumeState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String key = REDIS_STATE_PREFIX + state;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        stringRedisTemplate.delete(key);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            StatePayload sp = new StatePayload();
            sp.mode = (String) map.getOrDefault("mode", "login");
            Object uid = map.get("userId");
            sp.userId = uid instanceof Number ? ((Number) uid).intValue() : null;
            return sp;
        } catch (Exception e) {
            log.error("oauth state parse failed", e);
            return null;
        }
    }

    private static class StatePayload {
        String mode;
        Integer userId;
    }
}
