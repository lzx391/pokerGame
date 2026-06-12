package com.example.mgdemoplus.storage.impl;

import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.DpWebPathSupport;
import com.example.mgdemoplus.utils.DpImageFileSupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 本地磁盘存储：images / music / files 三套物理目录，与 {@code WebConfig} 静态映射一致。
 */
public class DpLocalFileObjectStorage implements DpObjectStorage {

    private final String imagesFileLocation;
    private final String musicFileLocation;
    private final String filesFileLocation;

    public DpLocalFileObjectStorage(
            String imagesFileLocation,
            String musicFileLocation,
            String filesFileLocation) {
        this.imagesFileLocation = imagesFileLocation;
        this.musicFileLocation = musicFileLocation;
        this.filesFileLocation = filesFileLocation;
    }

    @Override
    public void put(String webPath, InputStream data, long size, String contentType) throws IOException {
        if (!DpWebPathSupport.isValidWebPath(webPath)) {
            throw new IOException("invalid webPath: " + webPath);
        }
        File dest = resolveFile(webPath);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create directory: " + parent.getAbsolutePath());
        }
        Files.copy(data, dest.toPath());
    }

    @Override
    public Optional<StoredObject> get(String webPath) {
        if (!DpWebPathSupport.isValidWebPath(webPath)) {
            return Optional.empty();
        }
        File file = resolveFile(webPath);
        if (!file.isFile()) {
            return Optional.empty();
        }
        try {
            String contentType = DpWebPathSupport.guessContentType(webPath);
            return Optional.of(new StoredObject(new FileInputStream(file), file.length(), contentType));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String webPath) {
        if (!DpWebPathSupport.isValidWebPath(webPath)) {
            return false;
        }
        return resolveFile(webPath).isFile();
    }

    @Override
    public void delete(String webPath) throws IOException {
        if (!DpWebPathSupport.isValidWebPath(webPath)) {
            return;
        }
        File file = resolveFile(webPath);
        if (file.isFile() && !file.delete()) {
            throw new IOException("failed to delete: " + file.getAbsolutePath());
        }
    }

    @Override
    public List<String> listObjectKeysByPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return List.of();
        }
        String webPrefix = DpWebPathSupport.objectKeyToWebPath(keyPrefix);
        String pathPrefix = DpWebPathSupport.prefixOf(webPrefix);
        if (pathPrefix == null) {
            return List.of();
        }
        String filenamePrefix = webPrefix.substring(pathPrefix.length());
        File dir = resolveDir(pathPrefix);
        if (!dir.isDirectory()) {
            return List.of();
        }
        File[] files = dir.listFiles((d, name) -> name != null && name.startsWith(filenamePrefix));
        if (files == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (File f : files) {
            if (f.isFile()) {
                keys.add(DpWebPathSupport.webPathToObjectKey(pathPrefix + f.getName()));
            }
        }
        return keys;
    }

    private File resolveFile(String webPath) {
        String prefix = DpWebPathSupport.prefixOf(webPath);
        String filename = webPath.substring(prefix.length());
        return new File(resolveDir(prefix), filename);
    }

    private File resolveDir(String prefix) {
        String loc = switch (prefix) {
            case DpWebPathSupport.IMAGES_PREFIX -> imagesFileLocation;
            case DpWebPathSupport.MUSIC_PREFIX -> musicFileLocation;
            case DpWebPathSupport.FILES_PREFIX -> filesFileLocation;
            default -> throw new IllegalArgumentException("unknown prefix: " + prefix);
        };
        return new File(toPhysicalDir(loc));
    }

    private static String toPhysicalDir(String fileLocation) {
        if (fileLocation == null || fileLocation.isBlank()) {
            return "P:/javaworkspace/DPGameFiles/";
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

    /** 供头像缩略图回填等仍依赖目录扫描的场景。 */
    public File imagesDir() {
        return new File(DpImageFileSupport.toPhysicalDir(imagesFileLocation));
    }

    public String imagesPrefixLabel() {
        return "local:" + imagesDir().getAbsolutePath();
    }
}
