package com.example.mgdemoplus.gallery.impl;

import com.example.mgdemoplus.gallery.dto.DpGalleryUploadResult;
import com.example.mgdemoplus.gallery.mapper.DpGalleryItemMapper;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.storage.DpAvatarStorageSupport;
import com.example.mgdemoplus.storage.DpObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpGalleryItemServiceImplTest {

    @Mock
    private DpGalleryItemMapper dpGalleryItemMapper;
    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpCurrentUserSupport currentUserSupport;
    @Mock
    private DpObjectStorage objectStorage;
    @Mock
    private DpAvatarStorageSupport avatarStorageSupport;
    @Mock
    private DpSensitiveWordService sensitiveWordService;

    @InjectMocks
    private DpGalleryItemServiceImpl service;

    @Test
    void uploadItem_rejectsOversizedImage() {
        when(currentUserSupport.requireUserId()).thenReturn(3);
        byte[] payload = new byte[(int) DpGalleryItemServiceImpl.MAX_GALLERY_IMAGE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", payload);

        DpGalleryUploadResult result = service.uploadItem(file, "caption", 1);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("15MB");
    }

    @Test
    void uploadItem_rejectsUnsupportedExtension() {
        when(currentUserSupport.requireUserId()).thenReturn(3);
        MockMultipartFile file = new MockMultipartFile("file", "a.bmp", "image/bmp", new byte[] {1, 2, 3});

        DpGalleryUploadResult result = service.uploadItem(file, null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("jpg");
    }

    @Test
    void listItemsByUserId_returnsNullWhenUserMissing() {
        when(dpUserMapper.selectById(99)).thenReturn(null);

        assertThat(service.listItemsByUserId(99)).isNull();
    }

    @Test
    void listItemsByUserId_returnsMappedViews() {
        when(dpUserMapper.selectById(3)).thenReturn(new com.example.mgdemoplus.common.entity.DpUser());
        com.example.mgdemoplus.gallery.entity.DpGalleryItem row = new com.example.mgdemoplus.gallery.entity.DpGalleryItem();
        row.setId(10L);
        row.setImageUrl("/images/gallery-abc.jpg");
        row.setCaption("hello");
        row.setSortOrder(1);
        when(dpGalleryItemMapper.listByUserId(3)).thenReturn(java.util.List.of(row));

        var items = service.listItemsByUserId(3);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCaption()).isEqualTo("hello");
        assertThat(items.get(0).getPreviewUrl()).contains("_sm.webp");
    }
}
