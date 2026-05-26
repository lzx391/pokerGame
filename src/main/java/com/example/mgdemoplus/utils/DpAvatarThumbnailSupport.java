package com.example.mgdemoplus.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Locale;

/**
 * 头像缩略图：{@code {userId}_sm.webp}，128px 内接方框，WebP ~0.8。
 * 写入依赖 classpath 上的 WebP {@link ImageIO} SPI（如 webp-imageio）；缩放使用标准 {@link BufferedImage}。
 */
public final class DpAvatarThumbnailSupport {

    private static final Logger log = LoggerFactory.getLogger(DpAvatarThumbnailSupport.class);

    public static final int THUMB_MAX_EDGE = 128;
    public static final float WEBP_QUALITY = 0.8f;

    private DpAvatarThumbnailSupport() {}

    /**
     * 从已落盘的原图生成 {@code {userId}_sm.webp}。失败或跳过时返回 false，不抛异常。
     */
    public static boolean writeThumbnailIfPossible(File imagesDir, int userId, File sourceFile) {
        if (imagesDir == null || userId <= 0 || sourceFile == null || !sourceFile.isFile()) {
            return false;
        }
        File dest = new File(imagesDir, DpImageFileSupport.avatarThumbFilename(userId));
        try {
            BufferedImage src = readImage(sourceFile);
            if (src == null) {
                log.warn("avatar thumb skip: cannot decode userId={} file={}", userId, sourceFile.getName());
                return false;
            }
            BufferedImage scaled = scaleToFit(src, THUMB_MAX_EDGE);
            writeWebp(scaled, dest);
            return dest.isFile();
        } catch (IOException e) {
            log.warn("avatar thumb failed userId={} file={}: {}", userId, sourceFile.getName(), e.getMessage());
            if (dest.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
            }
            return false;
        }
    }

    static BufferedImage readImage(File sourceFile) throws IOException {
        String name = sourceFile.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gif")) {
            return readGifFirstFrame(sourceFile);
        }
        try (InputStream in = Files.newInputStream(sourceFile.toPath())) {
            return ImageIO.read(in);
        }
    }

    private static BufferedImage readGifFirstFrame(File sourceFile) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(sourceFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, false);
                if (reader.getNumImages(true) < 1) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    static BufferedImage scaleToFit(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        double scale = Math.min((double) maxEdge / w, (double) maxEdge / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        int type = src.getTransparency() == BufferedImage.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage dst = new BufferedImage(nw, nh, type);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static void writeWebp(BufferedImage image, File dest) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IOException("no ImageIO WebP writer (add webp-imageio to classpath)");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(dest)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0) {
                    param.setCompressionType(types[0]);
                }
                param.setCompressionQuality(WEBP_QUALITY);
            }
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
