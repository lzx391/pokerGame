package com.example.mgdemoplus.oauth.provider;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;

public interface DpOAuthProvider {

    String id();

    String displayName();

    boolean enabled();

    String buildAuthorizeUrl(String state);
    /**
     * 通过code调用接口取token
     * @param code
     * @return
     */
    OAuthTokenResponse exchangeCode(String code);
    /**
     * 通过accessToken调用接口取用户信息
     * @param accessToken
     * @return
     */
    OAuthUserProfile fetchUserProfile(String accessToken);
}
