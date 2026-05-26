package com.example.mgdemoplus.utils;

import java.io.File;
import java.util.Locale;
import java.util.Set;

/**
 * 头像等图片磁盘路径与 {@link com.example.mgdemoplus.config.WebConfig} {@code /images/**} 映射一致。
 */
public final class DpImageFileSupport {

    public static final Set<String> ALLOWED_IMAGE_EXT =
            Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    private DpImageFileSupport() {}

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

    /** 删除该用户所有 {@code {userId}.*} 头像文件（扩展名变更时清旧图）。 */
    public static void deleteUserAvatarFiles(String imagesFileLocation, int userId) {
        File dir = new File(toPhysicalDir(imagesFileLocation));
        if (!dir.isDirectory()) {
            return;
        }
        String prefix = userId + ".";
        File[] files = dir.listFiles((d, name) -> name != null && name.startsWith(prefix));
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}
