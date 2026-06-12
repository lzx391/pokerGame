package com.example.mgdemoplus.storage;

import java.util.Locale;
import java.util.Set;

/**
 * webPath 与 object key 互转、路径校验、Content-Type 推断。
 */
public final class DpWebPathSupport {

    public static final String IMAGES_PREFIX = "/images/";
    public static final String MUSIC_PREFIX = "/music/";
    public static final String FILES_PREFIX = "/files/";

    private static final Set<String> ALLOWED_PREFIXES = Set.of(IMAGES_PREFIX, MUSIC_PREFIX, FILES_PREFIX);

    private DpWebPathSupport() {}

    public static String webPathToObjectKey(String webPath) {
        if (webPath == null || webPath.isBlank()) {
            return null;
        }
        String normalized = webPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    public static String objectKeyToWebPath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return objectKey.startsWith("/") ? objectKey : "/" + objectKey;
    }

    /** 校验 webPath 合法且不含路径穿越。 */
    public static boolean isValidWebPath(String webPath) {
        if (webPath == null || webPath.isBlank()) {
            return false;
        }
        String path = webPath.trim();
        if (!path.startsWith("/")) {
            return false;
        }
        if (path.contains("..") || path.contains("\\")) {
            return false;
        }
        String prefix = prefixOf(path);
        if (prefix == null) {
            return false;
        }
        String remainder = path.substring(prefix.length());
        if (remainder.isEmpty() || remainder.contains("/")) {
            return false;
        }
        return true;
    }

    public static String prefixOf(String webPath) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (webPath.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    public static String filenameFromWebPath(String webPath) {
        if (!isValidWebPath(webPath)) {
            return null;
        }
        String prefix = prefixOf(webPath);
        return webPath.substring(prefix.length());
    }

    public static String guessContentType(String webPath) {
        String filename = filenameFromWebPath(webPath);
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lower.endsWith(".exe")) {
            return "application/vnd.microsoft.portable-executable";
        }
        if (lower.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        if (lower.endsWith(".msi")) {
            return "application/x-msi";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }
}
