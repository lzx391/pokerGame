package com.example.mgdemoplus.oauth.client;

public final class DingOAuthClientUtil {

    private DingOAuthClientUtil() {
    }

    public static String sanitizeBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        return body
                .replaceAll("(?i)(\"accessToken\"\\s*:\\s*\")[^\"]+\"", "$1***\"")
                .replaceAll("(?i)(\"access_token\"\\s*:\\s*\")[^\"]+\"", "$1***\"")
                .replaceAll("(?i)(\"refreshToken\"\\s*:\\s*\")[^\"]+\"", "$1***\"")
                .replaceAll("(?i)(\"refresh_token\"\\s*:\\s*\")[^\"]+\"", "$1***\"")
                .replaceAll("(?i)(\"mobile\"\\s*:\\s*\")[^\"]+\"", "$1***\"")
                .replaceAll("(?i)(\"email\"\\s*:\\s*\")[^\"]+\"", "$1***\"");
    }
}
