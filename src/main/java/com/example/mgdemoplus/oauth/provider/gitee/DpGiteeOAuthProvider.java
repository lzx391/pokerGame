package com.example.mgdemoplus.oauth.provider.gitee;

import com.example.mgdemoplus.oauth.client.DpOAuth2TokenExchangeService;

import com.example.mgdemoplus.oauth.client.DpOAuth2UserProfileLoader;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;

import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;

import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;

import org.springframework.security.oauth2.client.registration.ClientRegistration;

import org.springframework.stereotype.Component;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * 
 * Gitee OAuth Provider
 * 
 */

@Component

public class DpGiteeOAuthProvider implements DpOAuthProvider {

    private static final String PROVIDER_ID = "gitee";

    private final DpOAuth2TokenExchangeService tokenExchangeService;

    private final DpOAuth2UserProfileLoader profileLoader;

    public DpGiteeOAuthProvider(

            DpOAuth2TokenExchangeService tokenExchangeService,

            DpOAuth2UserProfileLoader profileLoader) {

        this.tokenExchangeService = tokenExchangeService;

        this.profileLoader = profileLoader;

    }

    @Override

    public String id() {

        return PROVIDER_ID;

    }

    @Override

    public String displayName() {

        return "Gitee";

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

                .queryParam("response_type", "code")

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

        return profileLoader.loadProfile(PROVIDER_ID, tokenResponse);

    }

    private ClientRegistration requireRegistration() {

        ClientRegistration registration = tokenExchangeService.findRegistration(PROVIDER_ID);

        if (registration == null) {

            throw new IllegalStateException("Gitee OAuth registration not configured");

        }

        return registration;

    }

}
