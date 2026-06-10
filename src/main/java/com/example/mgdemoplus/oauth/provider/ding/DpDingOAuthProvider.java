package com.example.mgdemoplus.oauth.provider.ding;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;

import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;

/**
 * 钉钉 OAuth2 登录 Provider（JustAuth {@code AuthDingTalkV2Request}；state 仍由 {@code DpOAuthService} 自管）。
 */
@Component
public class DpDingOAuthProvider implements DpOAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(DpDingOAuthProvider.class);
    private static final String PROVIDER_ID = "ding";
    private static final List<String> SCOPES = List.of("openid", "Contact.User.Read");

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public DpDingOAuthProvider(
            @Value("${spring.security.oauth2.client.registration.ding.client-id:}") String clientId,
            @Value("${spring.security.oauth2.client.registration.ding.client-secret:}") String clientSecret,
            @Value("${spring.security.oauth2.client.registration.ding.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public String displayName() {
        return "钉钉";
    }

    @Override
    public boolean enabled() {
        return hasText(clientId) && hasText(clientSecret);
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        requireEnabled();
        return authRequest().authorize(state);
    }

    @Override
    public OAuthTokenResponse exchangeCode(String code) {
        if (!enabled()) {
            log.error("dingding OAuth not configured");
            return new OAuthTokenResponse(null);
        }
        try {
            AuthCallback callback = AuthCallback.builder().code(code).build();
            AuthToken token = authRequest().exchangeToken(callback);
            if (token == null || !hasText(token.getAccessToken())) {
                log.error("dingding JustAuth userAccessToken failed");
                return new OAuthTokenResponse(null);
            }
            return new OAuthTokenResponse(token.getAccessToken(), token.getUnionId(), token.getOpenId());
        } catch (AuthException e) {
            log.error("dingding JustAuth token exchange failed: {}", e.getMessage());
            return new OAuthTokenResponse(null);
        } catch (Exception e) {
            log.error("dingding JustAuth token exchange failed", e);
            return new OAuthTokenResponse(null);
        }
    }

    @Override
    public OAuthUserProfile fetchUserProfile(OAuthTokenResponse tokenResponse) {
        if (tokenResponse == null || !tokenResponse.isSuccess()) {
            return null;
        }

        OAuthUserProfile contactProfile = fetchJustAuthProfile(tokenResponse);
        if (contactProfile != null && hasOpenId(contactProfile)) {
            return contactProfile;
        }

        OAuthUserProfile tokenProfile = profileFromTokenMetadata(tokenResponse);
        if (tokenProfile != null) {
            if (contactProfile == null) {
                log.info("dingding profile from userAccessToken fallback openId={} (contact/users/me unavailable)",
                        maskIdentity(tokenProfile.getOpenId()));
            } else {
                log.info("dingding profile merged token openId={} with partial contact data",
                        maskIdentity(tokenProfile.getOpenId()));
                return mergeProfiles(tokenProfile, contactProfile);
            }
            return tokenProfile;
        }

        return contactProfile;
    }

    private OAuthUserProfile fetchJustAuthProfile(OAuthTokenResponse tokenResponse) {
        try {
            AuthToken authToken = AuthToken.builder()
                    .accessToken(tokenResponse.getAccessToken())
                    .unionId(tokenResponse.getUnionId())
                    .openId(tokenResponse.getOpenId())
                    .build();
            AuthUser user = authRequest().loadUser(authToken);
            OAuthUserProfile profile = toOAuthUserProfile(user);
            if (profile == null) {
                log.warn("dingding JustAuth userInfo returned no openId/unionId");
            }
            return profile;
        } catch (AuthException e) {
            log.warn("dingding JustAuth userInfo failed: {} (check Contact.User.Read scope)", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("dingding JustAuth userInfo failed: {} (check Contact.User.Read scope)", e.getMessage());
            return null;
        }
    }

    private DpJustAuthDingTalkBridge authRequest() {
        AuthConfig config = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .scopes(SCOPES)
                .ignoreCheckState(true)
                .build();
        return new DpJustAuthDingTalkBridge(config);
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new IllegalStateException("Ding OAuth registration not configured");
        }
    }

    static OAuthUserProfile toOAuthUserProfile(AuthUser user) {
        if (user == null) {
            return null;
        }
        String unionId = user.getToken() != null ? user.getToken().getUnionId() : null;
        String openId = user.getToken() != null ? user.getToken().getOpenId() : null;
        String identity = preferredOpenId(unionId, user.getUuid(), openId);
        if (identity == null) {
            return null;
        }
        String avatarUrl = user.getAvatar();
        if (avatarUrl != null && avatarUrl.isBlank()) {
            avatarUrl = null;
        }
        String displayName = firstNonBlank(user.getNickname(), user.getUsername());
        return new OAuthUserProfile(identity, avatarUrl, displayName);
    }

    public static OAuthUserProfile profileFromTokenMetadata(OAuthTokenResponse tokenResponse) {
        if (tokenResponse == null) {
            return null;
        }
        String openId = preferredOpenId(tokenResponse.getUnionId(), tokenResponse.getOpenId());
        if (openId == null) {
            return null;
        }
        return new OAuthUserProfile(openId, null, null);
    }

    public static OAuthUserProfile mergeProfiles(OAuthUserProfile primary, OAuthUserProfile secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary == null) {
            return primary;
        }
        String openId = preferredOpenId(primary.getOpenId(), secondary.getOpenId());
        String avatarUrl = firstNonBlank(primary.getAvatarUrl(), secondary.getAvatarUrl());
        String displayName = firstNonBlank(primary.getDisplayName(), secondary.getDisplayName());
        return new OAuthUserProfile(openId, avatarUrl, displayName);
    }

    static boolean hasOpenId(OAuthUserProfile profile) {
        return profile != null && profile.getOpenId() != null && !profile.getOpenId().isBlank();
    }

    static String preferredOpenId(String... values) {
        return firstNonBlank(values);
    }

    static String maskIdentity(String identity) {
        if (identity == null || identity.length() <= 8) {
            return "***";
        }
        return identity.substring(0, 4) + "****" + identity.substring(identity.length() - 4);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** 测试用：手工构造 Provider（不经过 Spring 容器）。 */
    public static DpDingOAuthProvider forTest(String clientId, String clientSecret, String redirectUri) {
        return new DpDingOAuthProvider(clientId, clientSecret, redirectUri);
    }
}
