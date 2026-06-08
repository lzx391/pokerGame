package com.example.mgdemoplus.oauth.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.oauth.dto.OAuthCallbackResult;
import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.entity.DpSocialAuth;
import com.example.mgdemoplus.oauth.mapper.DpSocialAuthMapper;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.example.mgdemoplus.oauth.provider.DpOAuthProviderRegistry;
import com.example.mgdemoplus.utils.DpAvatarThumbnailSupport;
import com.example.mgdemoplus.utils.DpImageFileSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DpOAuthService {

    private static final Logger log = LoggerFactory.getLogger(DpOAuthService.class);
    private static final String REDIS_STATE_PREFIX = "oauth:state:";
    private static final long STATE_TTL_SECONDS = 600;
    /** Redis 6.2+ GETDEL 不可用；Lua GET+DEL 自 Redis 2.6 起原子消费。 */
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            """
                    local value = redis.call('GET', KEYS[1])
                    if value then
                      redis.call('DEL', KEYS[1])
                    end
                    return value
                    """,
            String.class);

    private final String imagesFileLocation;
    private final DpSocialAuthMapper socialAuthMapper;
    private final DpUserMapper dpUserMapper;
    private final DpSensitiveWordService sensitiveWordService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DpOAuthProviderRegistry providerRegistry;

    public DpOAuthService(
            @Value("${mgdemoplus.images.file-location:file:P:/javaworkspace/DPGameFiles/images/}") String imagesFileLocation,
            DpSocialAuthMapper socialAuthMapper,
            DpUserMapper dpUserMapper,
            DpSensitiveWordService sensitiveWordService,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            DpOAuthProviderRegistry providerRegistry) {
        this.imagesFileLocation = imagesFileLocation;
        this.socialAuthMapper = socialAuthMapper;
        this.dpUserMapper = dpUserMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.providerRegistry = providerRegistry;
    }
/**
 * 拼接提供商，回调地址，存state防止CSRF攻击
 * @param providerId
 * @return
 */
    public String buildAuthorizeUrl(String providerId) {
        DpOAuthProvider provider = providerRegistry.require(providerId);
        if (!provider.enabled()) {
            throw new IllegalStateException("OAuth 提供商未启用: " + providerId);
        }
//防止CSRF攻击的，state在后端这里有备份，这样攻击者携带的state无效，就会报错。
        String state = UUID.randomUUID().toString();
        storeState(state, Map.of("mode", "login", "provider", providerId));
        return provider.buildAuthorizeUrl(state);
    }

    private void storeState(String state, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(REDIS_STATE_PREFIX + state, json, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("oauth state redis write failed", e);
            throw new IllegalStateException("授权状态保存失败");
        }
    }
/**
 * 这里是用拿到的code去取信息
 * @param providerId
 * @param code
 * @param receivedState
 * @return
 */
    public OAuthCallbackResult handleCallback(String providerId, String code, String receivedState) {
        DpOAuthProvider provider = providerRegistry.require(providerId);
        if (!provider.enabled()) {
            return OAuthCallbackResult.fail("OAuth 提供商未启用");
        }

        Map<String, Object> statePayload = consumeState(receivedState);
        if (statePayload == null) {
            return OAuthCallbackResult.fail("无效或已过期的授权状态");
        }
        Object stateProvider = statePayload.get("provider");
        if (stateProvider == null || !providerId.equals(stateProvider.toString())) {
            return OAuthCallbackResult.fail("授权状态不匹配");
        }

        OAuthTokenResponse tokenResponse = provider.exchangeCode(code);
        if (!tokenResponse.isSuccess()) {
            return OAuthCallbackResult.fail(provider.displayName() + " 授权失败");
        }

        OAuthUserProfile profile = provider.fetchUserProfile(tokenResponse.getAccessToken());
        if (profile == null || profile.getOpenId() == null || profile.getOpenId().isBlank()) {
            return OAuthCallbackResult.fail("获取 " + provider.displayName() + " 用户信息失败");
        }

        return handleLoginMode(providerId, profile);
    }

    private OAuthCallbackResult handleLoginMode(String providerId, OAuthUserProfile profile) {
        String openId = profile.getOpenId();
//看是否已经注册过
        DpSocialAuth existing = socialAuthMapper.selectByProviderAndOpenId(providerId, openId);
        if (existing != null) {//如果已经注册过，则直接登录
            DpUser user = dpUserMapper.selectById(existing.getUserId());
            if (user == null) {
                return OAuthCallbackResult.fail("用户数据异常，请联系管理员");
            }
            return OAuthCallbackResult.loginSuccess(user.getNickname(), user.getId(), false, false);
        }

        String defaultNickname = openId;//如果未注册过，则生成默认昵称
        boolean needSetup = false;

        if (defaultNickname.length() > 10) {//如果昵称长度大于10，则截取前10位
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
        //注册用户，密码为空
        DpUser newUser = new DpUser();
        newUser.setNickname(defaultNickname);
        newUser.setPassword(null);
        dpUserMapper.registerUser(newUser);
        //注册成功后，将用户信息存入dp_social_auth表
        DpSocialAuth auth = new DpSocialAuth();
        auth.setUserId(newUser.getId());
        auth.setProvider(providerId);
        auth.setOpenId(openId);
        socialAuthMapper.insert(auth);

        if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isBlank()) {
            String localPath = downloadAndStoreAvatar(newUser.getId(), profile.getAvatarUrl());
            if (localPath != null) {
                dpUserMapper.updateAvatarUrl(newUser.getId(), localPath, LocalDateTime.now());
            }
        }

        return OAuthCallbackResult.loginSuccess(defaultNickname, newUser.getId(), true, needSetup);
    }

    private String downloadAndStoreAvatar(int userId, String avatarUrl) {
        try {
            String dir = DpImageFileSupport.toPhysicalDir(imagesFileLocation);
            File folder = new File(dir);
            if (!folder.exists() && !folder.mkdirs()) {
                return null;
            }
            DpImageFileSupport.deleteUserAvatarFiles(imagesFileLocation, userId);

            String storedFilename = userId + ".png";
            String webPath = "/images/" + storedFilename;

            URL url = URI.create(avatarUrl).toURL();
            try (InputStream in = url.openStream()) {
                Files.copy(in, new File(folder, storedFilename).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            DpAvatarThumbnailSupport.writeThumbnailIfPossible(folder, userId, new File(folder, storedFilename));
            return webPath;
        } catch (Exception e) {
            log.error("avatar download failed userId={} url={}", userId, avatarUrl, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> consumeState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String key = REDIS_STATE_PREFIX + state;
        String json = getAndDeleteState(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("oauth state parse failed", e);
            return null;
        }
    }

    private String getAndDeleteState(String key) {
        return stringRedisTemplate.execute(GET_AND_DELETE_SCRIPT, List.of(key));
    }
}
