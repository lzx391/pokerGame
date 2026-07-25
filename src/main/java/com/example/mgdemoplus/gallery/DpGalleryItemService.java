package com.example.mgdemoplus.gallery;

import com.example.mgdemoplus.gallery.dto.DpGalleryUploadResult;
import com.example.mgdemoplus.gallery.vo.DpGalleryItemView;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DpGalleryItemService {

    DpGalleryUploadResult uploadItem(MultipartFile file, String caption, Integer sortOrder);

    List<DpGalleryItemView> listMyItems();

    /** 指定用户的公开画廊；用户不存在时返回 {@code null}。 */
    List<DpGalleryItemView> listItemsByUserId(int userId);

    String updateItem(long id, String caption, Integer sortOrder);

    String deleteItem(long id);
}
