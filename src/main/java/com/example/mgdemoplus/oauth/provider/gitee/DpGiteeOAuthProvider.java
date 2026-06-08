package com.example.mgdemoplus.oauth.provider.gitee;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gitee OAuth Provider
 */
@Component
public class DpGiteeOAuthProvider implements DpOAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(DpGiteeOAuthProvider.class);
    private static final String PROVIDER_ID = "gitee";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DpGiteeOAuthProvider(
            @Value("${mgdemoplus.oauth.gitee.client-id:}") String clientId,
            @Value("${mgdemoplus.oauth.gitee.client-secret:}") String clientSecret,
            @Value("${mgdemoplus.oauth.gitee.redirect-uri:}") String redirectUri,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
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
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        // Gitee 的 scope 参数可选, 默认 read_user，state自己生成
        return "https://gitee.com/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&state=" + state
                + "&scope=user_info";
    }

    /**
     * 用 code 换 token
     */
    @Override
    public OAuthTokenResponse exchangeCode(String code) {
        try {
            String body = "grant_type=authorization_code"
                    + "&code=" + code
                    + "&client_id=" + clientId
                    + "&redirect_uri=" + redirectUri
                    + "&client_secret=" + clientSecret;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://gitee.com/oauth/token"))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            log.info("gitee token exchange status={} body={}", response.statusCode(), respBody);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(respBody, Map.class);
            String accessToken = (String) map.get("access_token");
            if (accessToken == null) {
                log.error("gitee token exchange returned error: {}", respBody);
            }
            return new OAuthTokenResponse(accessToken);
        } catch (Exception e) {
            log.error("gitee token exchange failed", e);
            return new OAuthTokenResponse(null);
        }
    }

    /**
     * 用 access_token 换用户信息
     */
    @Override
    public OAuthUserProfile fetchUserProfile(String accessToken) {
        try {
            // Gitee 用户接口，无需特殊 Accept
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://gitee.com/api/v5/user"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("User-Agent", "MGDemoPlus-OAuth")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            if (response.statusCode() != 200) {
                log.error("gitee user fetch status={} body={}", response.statusCode(), respBody);
                return null;
            }
            GiteeUserInfo giteeUser = objectMapper.readValue(respBody, GiteeUserInfo.class);
            if (giteeUser == null || giteeUser.getLogin() == null || giteeUser.getLogin().isBlank()) {
                log.error("gitee user fetch missing login body={}", respBody);
                return null;
            }
            return new OAuthUserProfile(giteeUser.getLogin(), giteeUser.getAvatarUrl(), giteeUser.getName());
        } catch (Exception e) {
            log.error("gitee user fetch failed", e);
            return null;
        }
    }

}
