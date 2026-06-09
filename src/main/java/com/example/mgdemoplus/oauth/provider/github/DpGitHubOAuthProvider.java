package com.example.mgdemoplus.oauth.provider.github;

import com.example.mgdemoplus.oauth.client.DpOAuth2TokenExchangeService;
import com.example.mgdemoplus.oauth.dto.OAuthHostUserInfo;
import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DpGitHubOAuthProvider implements DpOAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(DpGitHubOAuthProvider.class);
    private static final String PROVIDER_ID = "github";

    private final DpOAuth2TokenExchangeService tokenExchangeService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DpGitHubOAuthProvider(
            DpOAuth2TokenExchangeService tokenExchangeService,
            ObjectMapper objectMapper) {
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
        return "GitHub";
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
                .queryParam("redirect_uri", registration.getRedirectUri())
                .queryParam("state", state)
                .queryParam("scope", String.join(",", registration.getScopes()))
                .build(true)
                .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeCode(String code) {
        return tokenExchangeService.exchangeAuthorizationCode(PROVIDER_ID, code);
    }

    @Override
    public OAuthUserProfile fetchUserProfile(OAuthTokenResponse tokenResponse) {
        if (tokenResponse == null || !tokenResponse.isSuccess()) {
            return null;
        }
        String accessToken = tokenResponse.getAccessToken();
        ClientRegistration registration = requireRegistration();
        String userInfoUri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        try {
            String respBody = restClient.get()
                    .uri(userInfoUri)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MGDemoPlus-OAuth")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(String.class);

            OAuthHostUserInfo ghUser = objectMapper.readValue(respBody, OAuthHostUserInfo.class);
            if (ghUser == null || ghUser.getLogin() == null || ghUser.getLogin().isBlank()) {
                log.error("github user fetch missing login body={}", respBody);
                return null;
            }
            return new OAuthUserProfile(ghUser.getLogin(), ghUser.getAvatarUrl(), ghUser.getName());
        } catch (Exception e) {
            log.error("github user fetch failed", e);
            return null;
        }
    }

    private ClientRegistration requireRegistration() {
        ClientRegistration registration = tokenExchangeService.findRegistration(PROVIDER_ID);
        if (registration == null) {
            throw new IllegalStateException("GitHub OAuth registration not configured");
        }
        return registration;
    }
}
