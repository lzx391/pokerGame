package com.example.mgdemoplus.user.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.storage.DpAvatarStorageSupport;
import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.user.dto.DpAvatarUploadResult;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpUserServiceImplUploadAvatarTest {

    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpUserStatsMapper dpUserStatsMapper;
    @Mock
    private DpSensitiveWordService sensitiveWordService;
    @Mock
    private DpObjectStorage objectStorage;
    @Mock
    private DpAvatarStorageSupport avatarStorageSupport;

    @InjectMocks
    private DpUserServiceImpl service;

    @Test
    void uploadAvatar_persistsAvatarUpdatedAt() throws Exception {
        byte[] png = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0
        };
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", png);

        DpUser current = new DpUser();
        current.setId(7);
        DpUser stored = new DpUser();
        stored.setId(7);
        when(dpUserMapper.selectById(7)).thenReturn(stored);
        when(dpUserMapper.updateAvatarUrl(eq(7), eq("/images/7.png"), any(LocalDateTime.class))).thenReturn(1);
        doNothing().when(avatarStorageSupport).deleteWebPathFile(any());
        doNothing().when(avatarStorageSupport).deleteUserAvatarFiles(7);
        doNothing().when(objectStorage).put(anyString(), any(), anyLong(), anyString());

        DpAvatarUploadResult result = service.uploadAvatar(current, file);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAvatarUpdatedAt()).isNotNull().isPositive();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dpUserMapper).updateAvatarUrl(eq(7), eq("/images/7.png"), captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }
}
