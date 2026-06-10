package com.example.mgdemoplus.oauth.client;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

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

        DpOAuth2TokenExchangeService service = new DpOAuth2TokenExchangeService(
                id -> "github".equals(id) ? enabled : disabled);

        assertThat(service.isRegistrationEnabled("github")).isTrue();
        assertThat(service.isRegistrationEnabled("gitee")).isFalse();
    }

    @Test
    void exchangeAuthorizationCode_returnsNullWhenRegistrationMissing() {
        DpOAuth2TokenExchangeService service = new DpOAuth2TokenExchangeService(id -> null);

        OAuthTokenResponse response = service.exchangeAuthorizationCode("github", "code-1");

        assertThat(response.isSuccess()).isFalse();
    }
}
