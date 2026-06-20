package com.example.mgdemoplus.rbac.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.rbac.mapper.DpRbacQueryMapper;
import com.example.mgdemoplus.rbac.support.DpPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpPermissionServiceImplTest {

    @Mock
    private DpRbacQueryMapper rbacQueryMapper;
    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DpPermissionServiceImpl permissionService;

    @BeforeEach
    void wireRedisValueOps() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void hasPermi_usesCachedPermissions() throws Exception {
        DpUser user = new DpUser();
        user.setId(7);
        user.setNickname("viewer");
        when(dpUserMapper.selectByNickname("viewer")).thenReturn(user);
        when(valueOperations.get("mgdemo:cache:perm:7")).thenReturn("[\"game:hole_cards:view\"]");
        when(objectMapper.readValue(eq("[\"game:hole_cards:view\"]"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of(DpPermissionCodes.GAME_HOLE_CARDS_VIEW));

        assertThat(permissionService.hasPermi("viewer", DpPermissionCodes.GAME_HOLE_CARDS_VIEW)).isTrue();
    }

    @Test
    void evictUser_removesCachedPermissions() {
        permissionService.evictUser(7);
        verify(stringRedisTemplate).delete("mgdemo:cache:perm:7");
    }

    @Test
    void resolveByUserId_loadsFromDbAndCaches() throws Exception {
        when(valueOperations.get("mgdemo:cache:perm:9")).thenReturn(null);
        when(rbacQueryMapper.selectPermissionCodesByUserId(9)).thenReturn(List.of(DpPermissionCodes.GAME_HOLE_CARDS_VIEW));
        when(objectMapper.writeValueAsString(any(Set.class))).thenReturn("[\"game:hole_cards:view\"]");

        Set<String> permissions = permissionService.resolveByUserId(9);

        assertThat(permissions).containsExactly(DpPermissionCodes.GAME_HOLE_CARDS_VIEW);
        verify(valueOperations).set(eq("mgdemo:cache:perm:9"), eq("[\"game:hole_cards:view\"]"), eq(300L), eq(TimeUnit.SECONDS));
    }
}
