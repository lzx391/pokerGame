package com.example.mgdemoplus.utils;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 头像等图片磁盘路径与 {@link com.example.mgdemoplus.config.WebConfig} {@code /images/**} 映射一致。
 */
public final class DpImageFileSupport {

    public static final Set<String> ALLOWED_IMAGE_EXT =
            Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    /** 缩略图磁盘名，如 {@code 12_sm.webp} */
    public static final String AVATAR_THUMB_SUFFIX = "_sm.webp";

    /** 原图文件名：{@code {userId}.{ext}}，不含 {@code _sm} */
    private static final Pattern AVATAR_ORIGINAL_FILENAME =
            Pattern.compile("^(\\d+)\\.(jpg|jpeg|png|webp|gif)$", Pattern.CASE_INSENSITIVE);

    private DpImageFileSupport() {}

    public static String avatarThumbFilename(int userId) {
        return userId + AVATAR_THUMB_SUFFIX;
    }

    public static String avatarThumbWebPath(int userId) {
        return "/images/" + avatarThumbFilename(userId);
    }
/**
 * 合法物理路径
 * @param fileLocation
 * @return
 */
    public static String toPhysicalDir(String fileLocation) {
        if (fileLocation == null || fileLocation.isBlank()) {
            return "P:/javaworkspace/DPGameFiles/images/";
        }
        String s = fileLocation.trim();
        if (s.startsWith("file:")) {
            s = s.substring(5);
        }
        if (!s.endsWith("/") && !s.endsWith("\\")) {
            s = s + "/";
        }
        return s;
    }
/**
 * 获取文件扩展名
 * @param originalFilename
 * @return
 */
    public static String extensionOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String name = originalFilename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedImageExtension(String ext) {
        return ext != null && ALLOWED_IMAGE_EXT.contains(ext);
    }

    /** 从库内 web 路径（如 {@code /images/12.jpg}）解析磁盘文件名。 */
    public static String filenameFromWebPath(String webPath) {
        if (webPath == null || webPath.isBlank()) {
            return null;
        }
        String prefix = "/images/";
        if (!webPath.startsWith(prefix)) {
            return null;
        }
        String name = webPath.substring(prefix.length()).trim();
        if (name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        return name;
    }

    public static void deleteWebPathFile(String imagesFileLocation, String webPath) {
        String filename = filenameFromWebPath(webPath);
        if (filename == null) {
            return;
        }
        File f = new File(toPhysicalDir(imagesFileLocation), filename);
        if (f.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 删除该用户所有 {@code {userId}.*} 头像原图及 {@code {userId}_sm.webp}。 */
    public static void deleteUserAvatarFiles(String imagesFileLocation, int userId) {
        File dir = new File(toPhysicalDir(imagesFileLocation));
        if (!dir.isDirectory()) {
            return;
        }
        String prefix = userId + ".";
        File[] files = dir.listFiles((d, name) -> name != null && name.startsWith(prefix));
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        File thumb = new File(dir, avatarThumbFilename(userId));
        if (thumb.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            thumb.delete();
        }
    }

    /** 从目录名解析用户 id（仅 {@code 12.jpg} 形式，不含 {@code 12_sm.webp}）。 */
    public static Integer userIdFromAvatarOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        var m = AVATAR_ORIGINAL_FILENAME.matcher(filename.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
