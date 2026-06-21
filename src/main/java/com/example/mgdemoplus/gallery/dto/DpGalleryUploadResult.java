package com.example.mgdemoplus.gallery.dto;

import com.example.mgdemoplus.gallery.vo.DpGalleryItemView;

/** 画廊图片上传结果。 */
public class DpGalleryUploadResult {

    private final boolean success;
    private final String message;
    private final DpGalleryItemView item;

    private DpGalleryUploadResult(boolean success, String message, DpGalleryItemView item) {
        this.success = success;
        this.message = message;
        this.item = item;
    }

    public static DpGalleryUploadResult ok(DpGalleryItemView item) {
        return new DpGalleryUploadResult(true, null, item);
    }

    public static DpGalleryUploadResult fail(String message) {
        return new DpGalleryUploadResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public DpGalleryItemView getItem() {
        return item;
    }
}
