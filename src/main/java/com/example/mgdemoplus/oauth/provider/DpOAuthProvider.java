package com.example.mgdemoplus.oauth.provider;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;

public interface DpOAuthProvider {

    String id();

    String displayName();

    boolean enabled();

    String buildAuthorizeUrl(String state);

    OAuthTokenResponse exchangeCode(String code);

    OAuthUserProfile fetchUserProfile(String accessToken);
}
