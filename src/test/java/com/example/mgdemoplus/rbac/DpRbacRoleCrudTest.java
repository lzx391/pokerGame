package com.example.mgdemoplus.rbac;

import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.rbac.entity.DpRole;
import com.example.mgdemoplus.rbac.impl.DpRbacServiceImpl;
import com.example.mgdemoplus.rbac.mapper.DpPermissionMapper;
import com.example.mgdemoplus.rbac.mapper.DpRoleMapper;
import com.example.mgdemoplus.rbac.mapper.DpRolePermissionMapper;
import com.example.mgdemoplus.rbac.mapper.DpUserRoleMapper;
import com.example.mgdemoplus.rbac.vo.DpAdminRoleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpRbacRoleCrudTest {

    @Mock
    private DpRoleMapper dpRoleMapper;
    @Mock
    private DpPermissionMapper dpPermissionMapper;
    @Mock
    private DpRolePermissionMapper dpRolePermissionMapper;
    @Mock
    private DpUserRoleMapper dpUserRoleMapper;
    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpPermissionService dpPermissionService;

    @InjectMocks
    private DpRbacServiceImpl dpRbacService;

    @Test
    void createRole_success() {
        when(dpRoleMapper.selectIdByCode("MODERATOR")).thenReturn(null);
        when(dpRoleMapper.insert(any(DpRole.class))).thenAnswer(invocation -> {
            DpRole role = invocation.getArgument(0);
            role.setId(3L);
            return 1;
        });

        DpAdminRoleVO vo = dpRbacService.createRole("moderator", "版主");

        assertThat(vo.getId()).isEqualTo(3L);
        assertThat(vo.getCode()).isEqualTo("MODERATOR");
        assertThat(vo.getName()).isEqualTo("版主");
        assertThat(vo.getPermissionIds()).isEmpty();

        ArgumentCaptor<DpRole> captor = ArgumentCaptor.forClass(DpRole.class);
        verify(dpRoleMapper).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("MODERATOR");
        assertThat(captor.getValue().getName()).isEqualTo("版主");
    }

    @Test
    void createRole_duplicateCodeFails() {
        when(dpRoleMapper.selectIdByCode("MODERATOR")).thenReturn(3L);

        assertThatThrownBy(() -> dpRbacService.createRole("MODERATOR", "版主"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void createRole_invalidCodeFails() {
        assertThatThrownBy(() -> dpRbacService.createRole("1bad", "测试"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码格式无效");
    }

    @Test
    void deleteRole_builtInAdminFails() {
        DpRole admin = new DpRole();
        admin.setId(2L);
        admin.setCode("ADMIN");
        admin.setName("管理员");
        when(dpRoleMapper.selectById(2L)).thenReturn(admin);

        assertThatThrownBy(() -> dpRbacService.deleteRole(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内置角色不可删除");

        verify(dpRoleMapper, never()).deleteById(anyLong());
        verify(dpPermissionService, never()).evictAll();
    }

    @Test
    void deleteRole_withUsersStillSucceeds() {
        DpRole custom = new DpRole();
        custom.setId(5L);
        custom.setCode("MODERATOR");
        custom.setName("版主");
        when(dpRoleMapper.selectById(5L)).thenReturn(custom);

        dpRbacService.deleteRole(5L);

        verify(dpRoleMapper).deleteById(5L);
        verify(dpPermissionService).evictAll();
        verify(dpUserRoleMapper, never()).selectUserIdsByRoleId(anyLong());
    }

    @Test
    void deleteRole_emptyCustomRoleSucceeds() {
        DpRole custom = new DpRole();
        custom.setId(6L);
        custom.setCode("GUEST");
        custom.setName("访客");
        when(dpRoleMapper.selectById(6L)).thenReturn(custom);

        dpRbacService.deleteRole(6L);

        verify(dpRoleMapper).deleteById(6L);
        verify(dpPermissionService).evictAll();
    }
}
