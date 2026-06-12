package com.example.mgdemoplus.storage;

import com.example.mgdemoplus.utils.DpImageFileSupport;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 头像文件删除：适配本地磁盘与 MinIO。
 */
@Component
public class DpAvatarStorageSupport {

    private final DpObjectStorage objectStorage;

    public DpAvatarStorageSupport(DpObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    public void deleteWebPathFile(String webPath) {
        if (webPath == null || webPath.isBlank()) {
            return;
        }
        try {
            objectStorage.delete(webPath);
        } catch (IOException ignored) {
            // 删除失败不阻断主流程
        }
    }

    /** 删除该用户所有 {@code {userId}.*} 头像原图及 {@code {userId}_sm.webp}。 */
    public void deleteUserAvatarFiles(int userId) {
        String prefix = DpWebPathSupport.webPathToObjectKey(DpWebPathSupport.IMAGES_PREFIX + userId + ".");
        List<String> keys = objectStorage.listObjectKeysByPrefix(prefix);
        for (String key : keys) {
            try {
                objectStorage.delete(DpWebPathSupport.objectKeyToWebPath(key));
            } catch (IOException ignored) {
                // continue
            }
        }
        String thumbPath = DpImageFileSupport.avatarThumbWebPath(userId);
        try {
            objectStorage.delete(thumbPath);
        } catch (IOException ignored) {
            // continue
        }
    }
}
