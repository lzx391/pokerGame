package com.example.mgdemoplus.gallery.vo;

import com.example.mgdemoplus.gallery.entity.DpGalleryItem;
import com.example.mgdemoplus.utils.DpImageFileSupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 画廊条目 API 视图（含预览路径）。 */
public class DpGalleryItemView {

    private Long id;
    private String imageUrl;
    private String previewUrl;
    private String caption;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DpGalleryItemView from(DpGalleryItem item) {
        if (item == null) {
            return null;
        }
        DpGalleryItemView view = new DpGalleryItemView();
        view.setId(item.getId());
        view.setImageUrl(item.getImageUrl());
        view.setPreviewUrl(DpImageFileSupport.previewWebPathFromImageWebPath(item.getImageUrl()));
        view.setCaption(item.getCaption());
        view.setSortOrder(item.getSortOrder());
        view.setCreatedAt(item.getCreatedAt());
        view.setUpdatedAt(item.getUpdatedAt());
        return view;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("imageUrl", imageUrl);
        m.put("previewUrl", previewUrl);
        m.put("caption", caption);
        m.put("sortOrder", sortOrder);
        m.put("createdAt", createdAt);
        m.put("updatedAt", updatedAt);
        return m;
    }

    /** 他人查看画廊时的公开字段（不含时间戳等元数据）。 */
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("previewUrl", previewUrl);
        m.put("imageUrl", imageUrl);
        m.put("caption", caption);
        m.put("sortOrder", sortOrder);
        return m;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
