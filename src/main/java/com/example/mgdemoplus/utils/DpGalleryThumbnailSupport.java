package com.example.mgdemoplus.utils;

import java.io.File;
import java.util.Optional;

/**
 * 画廊预览图：{@code {uuid}_sm.webp}，最长边 800px，WebP ~0.8。
 */
public final class DpGalleryThumbnailSupport {

    public static final int PREVIEW_MAX_EDGE = 800;

    private DpGalleryThumbnailSupport() {}

    public static Optional<byte[]> generatePreviewBytes(File sourceFile) {
        return DpAvatarThumbnailSupport.generateThumbnailBytes(sourceFile, PREVIEW_MAX_EDGE);
    }
}
