package com.example.mgdemoplus.oauth.provider.github;

import com.example.mgdemoplus.oauth.dto.OAuthTokenResponse;
import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.example.mgdemoplus.oauth.provider.DpOAuthProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class DpGitHubOAuthProvider implements DpOAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(DpGitHubOAuthProvider.class);
    private static final String PROVIDER_ID = "github";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DpGitHubOAuthProvider(
            @Value("${mgdemoplus.oauth.github.client-id:}") String clientId,
            @Value("${mgdemoplus.oauth.github.client-secret:}") String clientSecret,
            @Value("${mgdemoplus.oauth.github.redirect-uri:}") String redirectUri,
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
        return "GitHub";
    }

    @Override
    public boolean enabled() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&state=" + state
                + "&scope=read:user";
    }

    @Override
    public OAuthTokenResponse exchangeCode(String code) {
        try {
            String body = "client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&code=" + code
                    + "&redirect_uri=" + redirectUri;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://github.com/login/oauth/access_token"))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            log.info("github token exchange status={} body={}", response.statusCode(), respBody);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(respBody, Map.class);
            String accessToken = (String) map.get("access_token");
            if (accessToken == null) {
                log.error("github token exchange returned error: {}", respBody);
            }
            return new OAuthTokenResponse(accessToken);
        } catch (Exception e) {
            log.error("github token exchange failed", e);
            return new OAuthTokenResponse(null);
        }
    }

    @Override
    public OAuthUserProfile fetchUserProfile(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/user"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MGDemoPlus-OAuth")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            if (response.statusCode() != 200) {
                log.error("github user fetch status={} body={}", response.statusCode(), respBody);
                return null;
            }
            GitHubUserInfo ghUser = objectMapper.readValue(respBody, GitHubUserInfo.class);
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
}
