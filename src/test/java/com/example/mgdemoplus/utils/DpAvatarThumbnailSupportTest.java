package com.example.mgdemoplus.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpAvatarThumbnailSupportTest {

    @TempDir
    File tempDir;

    @Test
    void writeThumbnail_createsSmWebp() throws Exception {
        File original = new File(tempDir, "42.png");
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        ImageIO.write(img, "png", original);

        assertTrue(DpAvatarThumbnailSupport.writeThumbnailIfPossible(tempDir, 42, original));

        File thumb = new File(tempDir, DpImageFileSupport.avatarThumbFilename(42));
        assertTrue(thumb.isFile());
        assertTrue(thumb.length() > 0);
    }

    @Test
    void deleteUserAvatarFiles_removesOriginalAndThumb(@TempDir File dir) throws Exception {
        File original = new File(dir, "7.jpg");
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpg", original);
        DpAvatarThumbnailSupport.writeThumbnailIfPossible(dir, 7, original);

        DpImageFileSupport.deleteUserAvatarFiles("file:" + dir.getAbsolutePath().replace('\\', '/') + "/", 7);

        assertFalse(original.exists());
        assertFalse(new File(dir, DpImageFileSupport.avatarThumbFilename(7)).exists());
    }

    @Test
    void userIdFromAvatarOriginalFilename_parsesAndRejectsThumb() {
        assertEquals(12, DpImageFileSupport.userIdFromAvatarOriginalFilename("12.jpg"));
        assertNull(DpImageFileSupport.userIdFromAvatarOriginalFilename("12_sm.webp"));
    }
}
