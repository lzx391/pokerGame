package com.example.mgdemoplus.oauth.provider.dingding;

import com.example.mgdemoplus.controller.DpOAuthController;
import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.provider.ding.DpDingOAuthProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DpDingOAuthProviderTest {

    private static DpDingOAuthProvider testProvider() {
        return DpDingOAuthProvider.forTest(
                "ding-client-id",
                "secret",
                "http://localhost:8080/oauth/ding/callback");
    }

    @Test
    void buildAuthorizeUrl_usesJustAuthV2EndpointWithContactScope() {
        DpDingOAuthProvider provider = testProvider();

        String url = provider.buildAuthorizeUrl("state-abc");

        assertThat(url).startsWith("https://login.dingtalk.com/oauth2/challenge.htm");
        assertThat(url).contains("client_id=ding-client-id");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=openid%2CContact.User.Read");
        assertThat(url).contains("state=state-abc");
        assertThat(url).contains("redirect_uri=http://localhost:8080/oauth/ding/callback");
        assertThat(url).contains("prompt=consent");
    }

    @Test
    void resolveAuthorizationCode_prefersStandardCodeThenAuthCode() {
        assertThat(DpOAuthController.resolveAuthorizationCode("code-1", "auth-1")).isEqualTo("code-1");
        assertThat(DpOAuthController.resolveAuthorizationCode(null, "auth-1")).isEqualTo("auth-1");
        assertThat(DpOAuthController.resolveAuthorizationCode("", "auth-1")).isEqualTo("auth-1");
        assertThat(DpOAuthController.resolveAuthorizationCode(null, null)).isNull();
    }

    @Test
    void profileFromTokenMetadata_prefersUnionIdAsOpenId() {
        OAuthTokenResponse tokenResponse = new OAuthTokenResponse("token", "union-main", "open-main");

        OAuthUserProfile profile = DpDingOAuthProvider.profileFromTokenMetadata(tokenResponse);

        assertThat(profile.getOpenId()).isEqualTo("union-main");
    }

    @Test
    void mergeProfiles_combinesTokenAndContactFields() {
        OAuthUserProfile tokenProfile = new OAuthUserProfile("union-1", null, null);
        OAuthUserProfile contactProfile = new OAuthUserProfile("open-1", "https://example.com/a.png", "Nick");

        OAuthUserProfile merged = DpDingOAuthProvider.mergeProfiles(tokenProfile, contactProfile);

        assertThat(merged.getOpenId()).isEqualTo("union-1");
        assertThat(merged.getAvatarUrl()).isEqualTo("https://example.com/a.png");
        assertThat(merged.getDisplayName()).isEqualTo("Nick");
    }
}
