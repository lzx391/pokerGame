package com.example.mgdemoplus.oauth.client;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Service;

/**
 * 统一 OAuth2 authorization_code → access_token 交换（Spring OAuth2 Client）。
 * authorize URL、Redis state、callback、JWT 仍由 {@code DpOAuthController} / {@code DpOAuthService} 自管。
 */
@Service
public class DpOAuth2TokenExchangeService {

    private static final Logger log = LoggerFactory.getLogger(DpOAuth2TokenExchangeService.class);
    private static final String EXTERNAL_STATE = "dp-oauth-state-managed-externally";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient;

    public DpOAuth2TokenExchangeService(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.accessTokenResponseClient = new RestClientAuthorizationCodeTokenResponseClient();
    }

    public boolean isRegistrationEnabled(String registrationId) {
        ClientRegistration registration = findRegistration(registrationId);
        return registration != null
                && registration.getClientId() != null && !registration.getClientId().isBlank()
                && registration.getClientSecret() != null && !registration.getClientSecret().isBlank();
    }

    public ClientRegistration findRegistration(String registrationId) {
        return clientRegistrationRepository.findByRegistrationId(registrationId);
    }

    public OAuthTokenResponse exchangeAuthorizationCode(String registrationId, String code) {
        OAuth2AccessTokenResponse response = exchangeAuthorizationCodeRaw(registrationId, code);
        if (response == null || response.getAccessToken() == null) {
            return new OAuthTokenResponse(null);
        }
        String accessToken = response.getAccessToken().getTokenValue();
        if (accessToken == null || accessToken.isBlank()) {
            log.error("oauth2 token exchange returned empty access token registrationId={}", registrationId);
            return new OAuthTokenResponse(null);
        }
        return new OAuthTokenResponse(accessToken);
    }

    public OAuth2AccessTokenResponse exchangeAuthorizationCodeRaw(String registrationId, String code) {
        ClientRegistration registration = findRegistration(registrationId);
        if (registration == null) {
            log.error("oauth2 client registration not found registrationId={}", registrationId);
            return null;
        }
        if (!isRegistrationEnabled(registrationId)) {
            log.error("oauth2 client registration disabled registrationId={}", registrationId);
            return null;
        }

        try {
            OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                    .clientId(registration.getClientId())
                    .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                    .redirectUri(registration.getRedirectUri())
                    .scopes(registration.getScopes())
                    .state(EXTERNAL_STATE)
                    .build();

            OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success(code)
                    .state(authorizationRequest.getState())
                    .redirectUri(registration.getRedirectUri())
                    .build();

            OAuth2AuthorizationExchange authorizationExchange =
                    new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse);

            OAuth2AuthorizationCodeGrantRequest grantRequest =
                    new OAuth2AuthorizationCodeGrantRequest(registration, authorizationExchange);

            OAuth2AccessTokenResponse tokenResponse = accessTokenResponseClient.getTokenResponse(grantRequest);
            log.info("oauth2 token exchange ok registrationId={}", registrationId);
            return tokenResponse;
        } catch (OAuth2AuthorizationException e) {
            log.error("oauth2 token exchange failed registrationId={} error={}", registrationId, e.getError(), e);
            return null;
        } catch (Exception e) {
            log.error("oauth2 token exchange failed registrationId={}", registrationId, e);
            return null;
        }
    }

}
