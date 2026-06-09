package com.example.mgdemoplus.oauth.client;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DpOAuth2UserProfileLoaderTest {

    @Test
    void mapOAuth2User_prefersLoginOverNameAttribute() {
        OAuth2User user = DpOAuth2UserProfileLoader.oauth2UserFromAttributes(
                Map.of(
                        "id", 1,
                        "login", "octocat",
                        "avatar_url", "https://avatars.githubusercontent.com/u/1",
                        "name", "The Octocat"),
                "id");

        OAuthUserProfile profile = DpOAuth2UserProfileLoader.mapOAuth2User(user);

        assertThat(profile.getOpenId()).isEqualTo("octocat");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/1");
        assertThat(profile.getDisplayName()).isEqualTo("The Octocat");
    }

    @Test
    void mapOAuth2User_fallsBackToNameWhenLoginMissing() {
        OAuth2User user = DpOAuth2UserProfileLoader.oauth2UserFromAttributes(
                Map.of("id", 42, "name", "Anonymous"),
                "id");

        OAuthUserProfile profile = DpOAuth2UserProfileLoader.mapOAuth2User(user);

        assertThat(profile.getOpenId()).isEqualTo("42");
        assertThat(profile.getDisplayName()).isEqualTo("Anonymous");
    }

    @Test
    void loadProfile_delegatesToOAuth2UserService() {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> userService = userRequest -> {
            assertThat(userRequest.getAccessToken().getTokenValue()).isEqualTo("access-token");
            return DpOAuth2UserProfileLoader.oauth2UserFromAttributes(
                    Map.of("login", "gitee-user", "avatar_url", "https://example.com/a.png"),
                    "login");
        };

        DpOAuth2TokenExchangeService tokenService = new DpOAuth2TokenExchangeService(
                id -> org.springframework.security.oauth2.client.registration.ClientRegistration
                        .withRegistrationId("gitee")
                        .clientId("id")
                        .clientSecret("secret")
                        .redirectUri("http://localhost/callback")
                        .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationUri("https://gitee.com/oauth/authorize")
                        .tokenUri("https://gitee.com/oauth/token")
                        .userInfoUri("https://gitee.com/api/v5/user")
                        .userNameAttributeName("login")
                        .build(),
                req -> {
                    throw new UnsupportedOperationException("ding not used");
                });

        DpOAuth2UserProfileLoader loader = new DpOAuth2UserProfileLoader(tokenService, userService);

        OAuthUserProfile profile = loader.loadProfile("gitee", new OAuthTokenResponse("access-token"));

        assertThat(profile.getOpenId()).isEqualTo("gitee-user");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://example.com/a.png");
    }

    @Test
    void loadProfile_returnsNullForFailedToken() {
        DpOAuth2TokenExchangeService tokenService = new DpOAuth2TokenExchangeService(
                id -> null,
                req -> {
                    throw new UnsupportedOperationException("ding not used");
                });
        DpOAuth2UserProfileLoader loader = new DpOAuth2UserProfileLoader(tokenService);

        assertThat(loader.loadProfile("github", new OAuthTokenResponse(null))).isNull();
        assertThat(loader.loadProfile("github", null)).isNull();
    }
}
