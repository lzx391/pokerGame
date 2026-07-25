package com.example.mgdemoplus.gallery.impl;

import com.example.mgdemoplus.gallery.DpGalleryItemService;
import com.example.mgdemoplus.gallery.dto.DpGalleryUploadResult;
import com.example.mgdemoplus.gallery.entity.DpGalleryItem;
import com.example.mgdemoplus.gallery.mapper.DpGalleryItemMapper;
import com.example.mgdemoplus.gallery.vo.DpGalleryItemView;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.storage.DpAvatarStorageSupport;
import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.DpWebPathSupport;
import com.example.mgdemoplus.user.impl.DpUserServiceImpl;
import com.example.mgdemoplus.utils.DpGalleryThumbnailSupport;
import com.example.mgdemoplus.utils.DpImageFileSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DpGalleryItemServiceImpl implements DpGalleryItemService {

    public static final long MAX_GALLERY_IMAGE_BYTES = 15L * 1024 * 1024;

    @Autowired
    private DpGalleryItemMapper dpGalleryItemMapper;
    @Autowired
    private DpUserMapper dpUserMapper;
    @Autowired
    private DpCurrentUserSupport currentUserSupport;
    @Autowired
    private DpObjectStorage objectStorage;
    @Autowired
    private DpAvatarStorageSupport avatarStorageSupport;
    @Autowired
    private DpSensitiveWordService sensitiveWordService;

    @Override
    public DpGalleryUploadResult uploadItem(MultipartFile file, String caption, Integer sortOrder) {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return DpGalleryUploadResult.fail("未登录或登录已失效");
        }
        if (file == null || file.isEmpty()) {
            return DpGalleryUploadResult.fail("请选择图片文件");
        }
        if (file.getSize() > MAX_GALLERY_IMAGE_BYTES) {
            return DpGalleryUploadResult.fail("图片不能超过 15MB");
        }
        String ext = DpImageFileSupport.extensionOf(file.getOriginalFilename());
        if (!DpImageFileSupport.isAllowedImageExtension(ext)) {
            return DpGalleryUploadResult.fail("仅支持 jpg、png、webp、gif");
        }
        String trimmedCaption = caption != null ? caption.trim() : null;
        if (trimmedCaption != null && !trimmedCaption.isEmpty()
                && sensitiveWordService.containsSensitive(trimmedCaption)) {
            return DpGalleryUploadResult.fail(DpUserServiceImpl.MSG_SENSITIVE);
        }

        String storedFilename = UUID.randomUUID() + ext;
        String webPath = DpWebPathSupport.IMAGES_PREFIX + storedFilename;
        String previewPath = DpImageFileSupport.previewWebPathFromImageWebPath(webPath);
        Path tempOriginal = null;
        try {
            tempOriginal = Files.createTempFile("gallery-", ext);
            file.transferTo(tempOriginal);
            String contentType = DpWebPathSupport.guessContentType(webPath);
            objectStorage.put(webPath, Files.newInputStream(tempOriginal), Files.size(tempOriginal), contentType);
            DpGalleryThumbnailSupport.generatePreviewBytes(tempOriginal.toFile()).ifPresent(previewBytes -> {
                try {
                    objectStorage.put(previewPath, new ByteArrayInputStream(previewBytes), previewBytes.length,
                            "image/webp");
                } catch (IOException ignored) {
                    // 预览图失败不阻断主流程
                }
            });
        } catch (IOException e) {
            avatarStorageSupport.deleteWebPathFile(webPath);
            avatarStorageSupport.deleteWebPathFile(previewPath);
            return DpGalleryUploadResult.fail("保存文件失败");
        } finally {
            if (tempOriginal != null) {
                try {
                    Files.deleteIfExists(tempOriginal);
                } catch (IOException ignored) {
                    // cleanup best-effort
                }
            }
        }

        DpGalleryItem row = new DpGalleryItem();
        row.setUserId(userId);
        row.setImageUrl(webPath);
        row.setCaption(trimmedCaption != null && !trimmedCaption.isEmpty() ? trimmedCaption : null);
        row.setSortOrder(sortOrder != null ? sortOrder : 0);
        if (dpGalleryItemMapper.insert(row) != 1) {
            avatarStorageSupport.deleteWebPathFile(webPath);
            avatarStorageSupport.deleteWebPathFile(previewPath);
            return DpGalleryUploadResult.fail("保存条目失败");
        }
        return DpGalleryUploadResult.ok(DpGalleryItemView.from(row));
    }

    @Override
    public List<DpGalleryItemView> listMyItems() {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return List.of();
        }
        return listItemsByUserId(userId);
    }

    @Override
    public List<DpGalleryItemView> listItemsByUserId(int userId) {
        if (dpUserMapper.selectById(userId) == null) {
            return null;
        }
        return dpGalleryItemMapper.listByUserId(userId).stream()
                .map(DpGalleryItemView::from)
                .collect(Collectors.toList());
    }

    @Override
    public String updateItem(long id, String caption, Integer sortOrder) {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return "未登录或登录已失效";
        }
        DpGalleryItem existing = dpGalleryItemMapper.selectByIdAndUserId(id, userId);
        if (existing == null) {
            return "条目不存在";
        }
        if (caption != null) {
            String trimmed = caption.trim();
            if (!trimmed.isEmpty() && sensitiveWordService.containsSensitive(trimmed)) {
                return DpUserServiceImpl.MSG_SENSITIVE;
            }
            existing.setCaption(trimmed.isEmpty() ? null : trimmed);
        }
        if (sortOrder != null) {
            existing.setSortOrder(sortOrder);
        }
        if (dpGalleryItemMapper.updateByIdAndUserId(existing) != 1) {
            return "更新失败";
        }
        return null;
    }

    @Override
    public String deleteItem(long id) {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return "未登录或登录已失效";
        }
        DpGalleryItem existing = dpGalleryItemMapper.selectByIdAndUserId(id, userId);
        if (existing == null) {
            return "条目不存在";
        }
        deleteItemFiles(existing.getImageUrl());
        if (dpGalleryItemMapper.deleteByIdAndUserId(id, userId) != 1) {
            return "删除失败";
        }
        return null;
    }

    private void deleteItemFiles(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        avatarStorageSupport.deleteWebPathFile(imageUrl);
        avatarStorageSupport.deleteWebPathFile(DpImageFileSupport.previewWebPathFromImageWebPath(imageUrl));
    }
}
