package com.example.mgdemoplus.config;

import com.example.mgdemoplus.utils.DpAvatarThumbnailSupport;
import com.example.mgdemoplus.utils.DpImageFileSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 可选：启动时扫描图片目录，为仅有原图的用户生成 {@code {userId}_sm.webp}。
 * 默认关闭，见 {@code mgdemoplus.images.backfill-thumbs}。
 */
@Component
public class DpAvatarThumbBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DpAvatarThumbBackfillRunner.class);

    @Value("${mgdemoplus.images.backfill-thumbs:false}")
    private boolean backfillThumbs;

    @Value("${mgdemoplus.images.file-location:file:P:/javaworkspace/DPGameFiles/images/}")
    private String imagesFileLocation;

    @Override
    public void run(ApplicationArguments args) {
        if (!backfillThumbs) {
            return;
        }
        File dir = new File(DpImageFileSupport.toPhysicalDir(imagesFileLocation));
        if (!dir.isDirectory()) {
            log.warn("avatar thumb backfill: images dir missing {}", dir.getAbsolutePath());
            return;
        }
        File[] entries = dir.listFiles(File::isFile);
        if (entries == null) {
            return;
        }
        int created = 0;
        int skipped = 0;
        for (File entry : entries) {
            Integer userId = DpImageFileSupport.userIdFromAvatarOriginalFilename(entry.getName());
            if (userId == null) {
                continue;
            }
            File thumb = new File(dir, DpImageFileSupport.avatarThumbFilename(userId));
            if (thumb.isFile()) {
                skipped++;
                continue;
            }
            if (DpAvatarThumbnailSupport.writeThumbnailIfPossible(dir, userId, entry)) {
                created++;
            }
        }
        log.info("avatar thumb backfill done: created={}, skippedExisting={}, dir={}",
                created, skipped, dir.getAbsolutePath());
    }
}
