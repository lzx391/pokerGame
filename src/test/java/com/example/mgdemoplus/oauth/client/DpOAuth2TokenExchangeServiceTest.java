package com.example.mgdemoplus.oauth.client;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DpOAuth2TokenExchangeServiceTest {

    @Test
    void isRegistrationEnabled_requiresClientIdAndSecret() {
        ClientRegistration enabled = ClientRegistration.withRegistrationId("github")
                .clientId("id")
                .clientSecret("secret")
                .redirectUri("http://localhost/callback")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .build();
        ClientRegistration disabled = ClientRegistration.withRegistrationId("gitee")
                .clientId("id")
                .clientSecret("")
                .redirectUri("http://localhost/callback")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://gitee.com/oauth/authorize")
                .tokenUri("https://gitee.com/oauth/token")
                .build();

        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> noopDingClient =
                req -> { throw new UnsupportedOperationException("ding not used"); };

        DpOAuth2TokenExchangeService service = new DpOAuth2TokenExchangeService(
                id -> "github".equals(id) ? enabled : disabled,
                noopDingClient);

        assertThat(service.isRegistrationEnabled("github")).isTrue();
        assertThat(service.isRegistrationEnabled("gitee")).isFalse();
    }

    @Test
    void exchangeAuthorizationCode_returnsNullWhenRegistrationMissing() {
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> noopDingClient =
                req -> { throw new UnsupportedOperationException("ding not used"); };

        DpOAuth2TokenExchangeService service = new DpOAuth2TokenExchangeService(
                id -> null,
                noopDingClient);

        OAuthTokenResponse response = service.exchangeAuthorizationCode("github", "code-1");

        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void exchangeAuthorizationCodeRaw_dingUsesCustomClient() {
        ClientRegistration ding = ClientRegistration.withRegistrationId("ding")
                .clientId("ding-id")
                .clientSecret("ding-secret")
                .redirectUri("http://localhost/oauth/ding/callback")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .build();

        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> dingClient = authorizationGrantRequest ->
                OAuth2AccessTokenResponse.withToken("ding-token")
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .additionalParameters(java.util.Map.of("unionId", "union-test"))
                        .build();

        DpOAuth2TokenExchangeService service = new DpOAuth2TokenExchangeService(
                id -> ding,
                dingClient);

        OAuth2AccessTokenResponse raw = service.exchangeAuthorizationCodeRaw("ding", "auth-code");

        assertThat(raw).isNotNull();
        assertThat(raw.getAccessToken().getTokenValue()).isEqualTo("ding-token");
        assertThat(raw.getAdditionalParameters()).containsEntry("unionId", "union-test");
    }
}
