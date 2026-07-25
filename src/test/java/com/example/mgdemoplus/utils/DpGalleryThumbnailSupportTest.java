package com.example.mgdemoplus.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpGalleryThumbnailSupportTest {

    @TempDir
    File tempDir;

    @Test
    void generatePreviewBytes_scalesToMaxEdge800() throws Exception {
        File original = new File(tempDir, "wide.png");
        BufferedImage img = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 1600, 900);
        g.dispose();
        ImageIO.write(img, "png", original);

        byte[] preview = DpGalleryThumbnailSupport.generatePreviewBytes(original)
                .orElseThrow(() -> new AssertionError("preview bytes missing"));
        BufferedImage scaled = ImageIO.read(new ByteArrayInputStream(preview));
        assertNotNull(scaled);
        assertTrue(scaled.getWidth() <= DpGalleryThumbnailSupport.PREVIEW_MAX_EDGE);
        assertTrue(scaled.getHeight() <= DpGalleryThumbnailSupport.PREVIEW_MAX_EDGE);
        assertEquals(800, scaled.getWidth());
        assertEquals(450, scaled.getHeight());
    }

    @Test
    void previewWebPathFromImageWebPath_replacesExtensionWithSmWebp() {
        assertEquals("/images/abc-uuid_sm.webp",
                DpImageFileSupport.previewWebPathFromImageWebPath("/images/abc-uuid.jpg"));
    }
}
