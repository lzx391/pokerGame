package com.example.mgdemoplus.oauth.provider.dingding;

import com.example.mgdemoplus.controller.DpOAuthController;
import com.example.mgdemoplus.oauth.client.DingOAuthClientUtil;
import com.example.mgdemoplus.oauth.client.dto.DingUserAccessTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.provider.ding.DingContactUserInfo;
import com.example.mgdemoplus.oauth.provider.ding.DpDingOAuthProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;

class DpDingOAuthProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ClientRegistration testDingRegistration() {
        return ClientRegistration.withRegistrationId("ding")
                .clientId("ding-client-id")
                .clientSecret("secret")
                .redirectUri("http://localhost:8080/oauth/ding/callback")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .scope("openid", "Contact.User.Read")
                .build();
    }

    @Test
    void buildAuthorizeUrl_usesOAuth2EndpointWithContactScope() {
        DpDingOAuthProvider provider = DpDingOAuthProvider.forTest(testDingRegistration(), objectMapper);

        String url = provider.buildAuthorizeUrl("state-abc");

        assertThat(url).startsWith("https://login.dingtalk.com/oauth2/auth");
        assertThat(url).contains("client_id=ding-client-id");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=openid%20Contact.User.Read");
        assertThat(url).contains("state=state-abc");
        assertThat(url).contains("redirect_uri=http://localhost:8080/oauth/ding/callback");
        assertThat(url).contains("prompt=consent");
    }

    @Test
    void userAccessTokenResponse_deserializesAccessToken() throws Exception {
        String json = """
                {"accessToken":"user-token-1","refreshToken":"refresh-1","expireIn":7200,"corpId":"corp-1"}
                """;

        DingUserAccessTokenResponse response = objectMapper.readValue(json, DingUserAccessTokenResponse.class);

        assertThat(response.getAccessToken()).isEqualTo("user-token-1");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-1");
        assertThat(response.getExpireIn()).isEqualTo(7200L);
        assertThat(response.getCorpId()).isEqualTo("corp-1");
    }

    @Test
    void userAccessTokenResponse_deserializesUnionIdAndOpenId() throws Exception {
        String json = """
                {"accessToken":"user-token-2","unionId":"union-from-token","openId":"open-from-token"}
                """;

        DingUserAccessTokenResponse response = objectMapper.readValue(json, DingUserAccessTokenResponse.class);

        assertThat(response.getAccessToken()).isEqualTo("user-token-2");
        assertThat(response.getUnionId()).isEqualTo("union-from-token");
        assertThat(response.getOpenId()).isEqualTo("open-from-token");
    }

    @Test
    void userAccessTokenResponse_deserializesSnakeCaseUnionId() throws Exception {
        String json = """
                {"access_token":"user-token-3","union_id":"union-snake","open_id":"open-snake"}
                """;

        DingUserAccessTokenResponse response = objectMapper.readValue(json, DingUserAccessTokenResponse.class);

        assertThat(response.getUnionId()).isEqualTo("union-snake");
        assertThat(response.getOpenId()).isEqualTo("open-snake");
    }

    @Test
    void userAccessTokenResponse_deserializesSnakeCaseAccessToken() throws Exception {
        String json = """
                {"access_token":"user-token-snake","refresh_token":"refresh-1","expires_in":3600,"corp_id":"corp-1"}
                """;

        DingUserAccessTokenResponse response = objectMapper.readValue(json, DingUserAccessTokenResponse.class);

        assertThat(response.getAccessToken()).isEqualTo("user-token-snake");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-1");
        assertThat(response.getExpireIn()).isEqualTo(3600L);
        assertThat(response.getCorpId()).isEqualTo("corp-1");
    }

    @Test
    void resolveAuthorizationCode_prefersStandardCodeThenAuthCode() {
        assertThat(DpOAuthController.resolveAuthorizationCode("code-1", "auth-1")).isEqualTo("code-1");
        assertThat(DpOAuthController.resolveAuthorizationCode(null, "auth-1")).isEqualTo("auth-1");
        assertThat(DpOAuthController.resolveAuthorizationCode("", "auth-1")).isEqualTo("auth-1");
        assertThat(DpOAuthController.resolveAuthorizationCode(null, null)).isNull();
    }

    @Test
    void contactUserInfo_deserializesSnakeCaseFields() throws Exception {
        String json = """
                {"nick":"李四","avatar_url":"https://static.dingtalk.com/b.jpg","open_id":"oid2","union_id":"uid2"}
                """;

        DingContactUserInfo info = objectMapper.readValue(json, DingContactUserInfo.class);

        assertThat(info.getNick()).isEqualTo("李四");
        assertThat(info.getAvatarUrl()).isEqualTo("https://static.dingtalk.com/b.jpg");
        assertThat(info.getOpenId()).isEqualTo("oid2");
        assertThat(info.getUnionId()).isEqualTo("uid2");
    }

    @Test
    void contactUserInfo_deserializesNickAndAvatar() throws Exception {
        String json = """
                {"nick":"张三","avatarUrl":"https://static.dingtalk.com/a.jpg","openId":"oid","unionId":"uid"}
                """;

        DingContactUserInfo info = objectMapper.readValue(json, DingContactUserInfo.class);

        assertThat(info.getNick()).isEqualTo("张三");
        assertThat(info.getAvatarUrl()).isEqualTo("https://static.dingtalk.com/a.jpg");
        assertThat(info.getUnionId()).isEqualTo("uid");
    }

    @Test
    void toOAuthUserProfile_prefersUnionIdAndMapsAvatar() {
        DingContactUserInfo contactUser = new DingContactUserInfo();
        contactUser.setUnionId("union-1");
        contactUser.setOpenId("open-1");
        contactUser.setNick("Nick");
        contactUser.setAvatarUrl("https://example.com/avatar.png");

        OAuthUserProfile profile = DpDingOAuthProvider.toOAuthUserProfile(contactUser);

        assertThat(profile.getOpenId()).isEqualTo("union-1");
        assertThat(profile.getDisplayName()).isEqualTo("Nick");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void toOAuthUserProfile_allowsNullAvatarWithoutBlocking() {
        DingContactUserInfo contactUser = new DingContactUserInfo();
        contactUser.setUnionId("union-2");
        contactUser.setNick("NoAvatar");

        OAuthUserProfile profile = DpDingOAuthProvider.toOAuthUserProfile(contactUser);

        assertThat(profile.getOpenId()).isEqualTo("union-2");
        assertThat(profile.getDisplayName()).isEqualTo("NoAvatar");
        assertThat(profile.getAvatarUrl()).isNull();
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

    @Test
    void sanitizeBody_masksSensitiveFields() {
        String sanitized = DingOAuthClientUtil.sanitizeBody(
                "{\"accessToken\":\"secret-token\",\"mobile\":\"15000000000\",\"unionId\":\"uid\"}");

        assertThat(sanitized).contains("\"accessToken\":\"***\"");
        assertThat(sanitized).contains("\"mobile\":\"***\"");
        assertThat(sanitized).contains("\"unionId\":\"uid\"");
    }
}
