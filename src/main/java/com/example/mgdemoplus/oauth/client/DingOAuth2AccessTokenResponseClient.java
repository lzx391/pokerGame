package com.example.mgdemoplus.oauth.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

import com.example.mgdemoplus.oauth.client.dto.DingUserAccessTokenResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 钉钉 userAccessToken 非标准 OAuth2 form POST，需 JSON body；unionId/openId 放入 additionalParameters。
 */
@Component
public class DingOAuth2AccessTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final Logger log = LoggerFactory.getLogger(DingOAuth2AccessTokenResponseClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DingOAuth2AccessTokenResponseClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
        ClientRegistration registration = authorizationGrantRequest.getClientRegistration();
        String code = authorizationGrantRequest.getAuthorizationExchange()
                .getAuthorizationResponse()
                .getCode();

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "clientId", registration.getClientId(),
                    "clientSecret", registration.getClientSecret(),
                    "code", code,
                    "grantType", "authorization_code"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(registration.getProviderDetails().getTokenUri()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            log.info("dingding userAccessToken status={} clientId={} redirectUri={} body={}",
                    response.statusCode(), maskClientId(registration.getClientId()),
                    registration.getRedirectUri(), DingOAuthClientUtil.sanitizeBody(respBody));

            if (response.statusCode() != 200) {
                throw oauth2Error("invalid_token_response",
                        "dingding userAccessToken http " + response.statusCode() + ": "
                                + DingOAuthClientUtil.sanitizeBody(respBody));
            }

            DingUserAccessTokenResponse tokenResponse = objectMapper.readValue(respBody, DingUserAccessTokenResponse.class);
            String accessToken = tokenResponse.getAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                throw oauth2Error("invalid_token_response",
                        "dingding userAccessToken missing accessToken: "
                                + DingOAuthClientUtil.sanitizeBody(respBody));
            }

            String unionId = firstNonBlank(
                    tokenResponse.getUnionId(),
                    extractIdentityFromJson(respBody, "unionId", "union_id", "unionid"),
                    extractUnionIdFromJwt(accessToken));
            String openId = firstNonBlank(
                    tokenResponse.getOpenId(),
                    extractIdentityFromJson(respBody, "openId", "open_id", "openid"));

            log.info("dingding userAccessToken ok expireIn={} corpId={} unionIdPresent={} openIdPresent={}",
                    tokenResponse.getExpireIn(), tokenResponse.getCorpId(),
                    unionId != null, openId != null);

            Map<String, Object> additionalParameters = new HashMap<>();
            if (unionId != null) {
                additionalParameters.put("unionId", unionId);
            }
            if (openId != null) {
                additionalParameters.put("openId", openId);
            }

            return OAuth2AccessTokenResponse.withToken(accessToken)
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(tokenResponse.getExpireIn())
                    .additionalParameters(additionalParameters)
                    .build();
        } catch (OAuth2AuthorizationException e) {
            throw e;
        } catch (Exception e) {
            log.error("dingding userAccessToken failed clientId={} redirectUri={}",
                    maskClientId(registration.getClientId()), registration.getRedirectUri(), e);
            throw oauth2Error("invalid_token_response", "dingding userAccessToken failed: " + e.getMessage());
        }
    }

    private OAuth2AuthorizationException oauth2Error(String code, String description) {
        return new OAuth2AuthorizationException(new OAuth2Error(code, description, null));
    }

    private String extractIdentityFromJson(String json, String... fieldNames) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            for (String fieldName : fieldNames) {
                String value = textOrNull(root, fieldName);
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception e) {
            log.debug("dingding identity json parse failed", e);
        }
        return null;
    }

    private String extractUnionIdFromJwt(String accessToken) {
        if (accessToken == null || !accessToken.contains(".")) {
            return null;
        }
        String[] parts = accessToken.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadBytes);
            return firstNonBlank(
                    textOrNull(payload, "unionId"),
                    textOrNull(payload, "unionid"),
                    textOrNull(payload, "union_id"),
                    textOrNull(payload, "sub"));
        } catch (Exception e) {
            log.debug("dingding accessToken is not a decodable JWT", e);
            return null;
        }
    }

    private static String padBase64(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "=".repeat(4 - mod);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    private static String maskClientId(String clientId) {
        if (clientId == null || clientId.length() <= 8) {
            return "***";
        }
        return clientId.substring(0, 4) + "****" + clientId.substring(clientId.length() - 4);
    }
}
