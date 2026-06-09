package com.example.mgdemoplus.oauth.provider.ding;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.mgdemoplus.oauth.client.DingOAuth2AccessTokenResponseClient;
import com.example.mgdemoplus.oauth.client.DingOAuthClientUtil;
import com.example.mgdemoplus.oauth.client.DpOAuth2TokenExchangeService;
import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 钉钉 OAuth2 登录 Provider（Spring OAuth2 Client 换 token + contact/users/me 取 profile）。
 */
@Component
public class DpDingOAuthProvider implements DpOAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(DpDingOAuthProvider.class);
    private static final String PROVIDER_ID = "ding";
    private static final String CONTACT_USERS_ME_URL = "https://api.dingtalk.com/v1.0/contact/users/me";

    private final DpOAuth2TokenExchangeService tokenExchangeService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DpDingOAuthProvider(DpOAuth2TokenExchangeService tokenExchangeService, ObjectMapper objectMapper) {
        this.tokenExchangeService = tokenExchangeService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
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
        return tokenExchangeService.isRegistrationEnabled(PROVIDER_ID);
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        ClientRegistration registration = requireRegistration();
        return UriComponentsBuilder.fromUriString(registration.getProviderDetails().getAuthorizationUri())
                .queryParam("client_id", registration.getClientId())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", registration.getScopes()))
                .queryParam("state", state)
                .queryParam("redirect_uri", registration.getRedirectUri())
                .queryParam("prompt", "consent")
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeCode(String code) {
        OAuth2AccessTokenResponse tokenResponse = tokenExchangeService.exchangeAuthorizationCodeRaw(PROVIDER_ID, code);
        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            log.error("dingding OAuth2 userAccessToken failed");
            return new OAuthTokenResponse(null);
        }
        String accessToken = tokenResponse.getAccessToken().getTokenValue();
        Map<String, Object> additional = tokenResponse.getAdditionalParameters();
        String unionId = stringParam(additional, "unionId");
        String openId = stringParam(additional, "openId");
        return new OAuthTokenResponse(accessToken, unionId, openId);
    }

    @Override
    public OAuthUserProfile fetchUserProfile(OAuthTokenResponse tokenResponse) {
        if (tokenResponse == null || !tokenResponse.isSuccess()) {
            return null;
        }

        OAuthUserProfile contactProfile = fetchContactProfile(tokenResponse.getAccessToken());
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

    private OAuthUserProfile fetchContactProfile(String accessToken) {
        try {
            String respBody = restClient.get()
                    .uri(CONTACT_USERS_ME_URL)
                    .header("x-acs-dingtalk-access-token", accessToken)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .body(String.class);

            DingContactUserInfo contactUser = objectMapper.readValue(respBody, DingContactUserInfo.class);
            OAuthUserProfile profile = toOAuthUserProfile(contactUser);
            if (profile == null) {
                log.warn("dingding contact/users/me returned no openId/unionId body={}",
                        DingOAuthClientUtil.sanitizeBody(respBody));
            }
            return profile;
        } catch (Exception e) {
            log.warn("dingding contact/users/me failed: {} (check Contact.User.Read scope)", e.getMessage());
            return null;
        }
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

    static String preferredOpenId(String unionId, String openId) {
        return firstNonBlank(unionId, openId);
    }

    static String maskIdentity(String identity) {
        if (identity == null || identity.length() <= 8) {
            return "***";
        }
        return identity.substring(0, 4) + "****" + identity.substring(identity.length() - 4);
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

    private static String stringParam(Map<String, Object> params, String key) {
        if (params == null) {
            return null;
        }
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    public static OAuthUserProfile toOAuthUserProfile(DingContactUserInfo contactUser) {
        if (contactUser == null) {
            return null;
        }
        String openId = contactUser.getUnionId();
        if (openId == null || openId.isBlank()) {
            openId = contactUser.getOpenId();
        }
        if (openId == null || openId.isBlank()) {
            return null;
        }
        String avatarUrl = contactUser.getAvatarUrl();
        if (avatarUrl != null && avatarUrl.isBlank()) {
            avatarUrl = null;
        }
        return new OAuthUserProfile(openId, avatarUrl, contactUser.getNick());
    }

    private ClientRegistration requireRegistration() {
        ClientRegistration registration = tokenExchangeService.findRegistration(PROVIDER_ID);
        if (registration == null) {
            throw new IllegalStateException("Ding OAuth registration not configured");
        }
        return registration;
    }

    /** 测试用：手工构造 Provider（不经过 Spring 容器）。 */
    public static DpDingOAuthProvider forTest(ClientRegistration registration, ObjectMapper objectMapper) {
        DpOAuth2TokenExchangeService tokenService = new DpOAuth2TokenExchangeService(
                id -> registration,
                new DingOAuth2AccessTokenResponseClient(objectMapper));
        return new DpDingOAuthProvider(tokenService, objectMapper);
    }
}
