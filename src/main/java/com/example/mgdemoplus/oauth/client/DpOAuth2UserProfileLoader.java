package com.example.mgdemoplus.oauth.client;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * Loads OAuth user profiles via Spring {@link DefaultOAuth2UserService} after manual code→token exchange.
 */
@Service
public class DpOAuth2UserProfileLoader {

    private static final Logger log = LoggerFactory.getLogger(DpOAuth2UserProfileLoader.class);

    private final DpOAuth2TokenExchangeService tokenExchangeService;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService;

    @Autowired
    public DpOAuth2UserProfileLoader(DpOAuth2TokenExchangeService tokenExchangeService) {
        this(tokenExchangeService, new DefaultOAuth2UserService());
    }

    DpOAuth2UserProfileLoader(
            DpOAuth2TokenExchangeService tokenExchangeService,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService) {
        this.tokenExchangeService = tokenExchangeService;
        this.oauth2UserService = oauth2UserService;
    }

    public OAuthUserProfile loadProfile(String registrationId, OAuthTokenResponse tokenResponse) {
        if (tokenResponse == null || !tokenResponse.isSuccess()) {
            return null;
        }
        ClientRegistration registration = tokenExchangeService.findRegistration(registrationId);
        if (registration == null) {
            log.error("oauth2 user profile load registration not found registrationId={}", registrationId);
            return null;
        }
        try {
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    tokenResponse.getAccessToken(),
                    null,
                    null,
                    registration.getScopes());
            OAuth2UserRequest userRequest = new OAuth2UserRequest(registration, accessToken);
            OAuth2User oauth2User = oauth2UserService.loadUser(userRequest);
            OAuthUserProfile profile = mapOAuth2User(oauth2User);
            if (profile == null) {
                log.error("oauth2 user profile missing openId registrationId={}", registrationId);
            }
            return profile;
        } catch (Exception e) {
            log.error("oauth2 user profile load failed registrationId={}", registrationId, e);
            return null;
        }
    }

    static OAuthUserProfile mapOAuth2User(OAuth2User oauth2User) {
        if (oauth2User == null) {
            return null;
        }
        String openId = stringAttribute(oauth2User, "login");
        if (openId == null || openId.isBlank()) {
            openId = oauth2User.getName();
        }
        if (openId == null || openId.isBlank()) {
            return null;
        }
        String avatarUrl = firstNonBlank(
                stringAttribute(oauth2User, "avatar_url"),
                stringAttribute(oauth2User, "avatarUrl"));
        String displayName = firstNonBlank(
                stringAttribute(oauth2User, "name"),
                stringAttribute(oauth2User, "nick"));
        return new OAuthUserProfile(openId, avatarUrl, displayName);
    }

    static OAuth2User oauth2UserFromAttributes(Map<String, Object> attributes, String nameAttributeKey) {
        return new DefaultOAuth2User(Collections.emptyList(), attributes, nameAttributeKey);
    }

    private static String stringAttribute(OAuth2User user, String key) {
        Object value = user.getAttributes().get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
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
}
