package com.example.mgdemoplus.rbac.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.rbac.DpPermissionService;
import com.example.mgdemoplus.rbac.DpRbacService;
import com.example.mgdemoplus.rbac.entity.DpPermission;
import com.example.mgdemoplus.rbac.entity.DpRole;
import com.example.mgdemoplus.rbac.mapper.DpPermissionMapper;
import com.example.mgdemoplus.rbac.mapper.DpRoleMapper;
import com.example.mgdemoplus.rbac.mapper.DpRolePermissionMapper;
import com.example.mgdemoplus.rbac.mapper.DpUserRoleMapper;
import com.example.mgdemoplus.rbac.vo.DpAdminPermissionVO;
import com.example.mgdemoplus.rbac.vo.DpAdminRoleVO;
import com.example.mgdemoplus.rbac.vo.DpAdminUserPageVO;
import com.example.mgdemoplus.rbac.vo.DpAdminUserVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DpRbacServiceImpl implements DpRbacService {

    private static final String ROLE_PLAYER = "PLAYER";

    @Autowired
    private DpRoleMapper dpRoleMapper;
    @Autowired
    private DpPermissionMapper dpPermissionMapper;
    @Autowired
    private DpRolePermissionMapper dpRolePermissionMapper;
    @Autowired
    private DpUserRoleMapper dpUserRoleMapper;
    @Autowired
    private DpUserMapper dpUserMapper;
    @Autowired
    private DpPermissionService dpPermissionService;

    @Override
    @Transactional
    public void bindPlayerRole(int userId) {
        if (userId <= 0) {
            return;
        }
        Long roleId = dpRoleMapper.selectIdByCode(ROLE_PLAYER);
        if (roleId == null) {
            return;
        }
        List<Long> existing = dpUserRoleMapper.selectRoleIdsByUserId(userId);
        if (existing != null && existing.contains(roleId)) {
            return;
        }
        dpUserRoleMapper.insert(userId, roleId);
        dpPermissionService.evictUser(userId);
    }

    @Override
    public List<DpAdminRoleVO> listRolesWithPermissions() {
        List<DpRole> roles = dpRoleMapper.selectList(new LambdaQueryWrapper<DpRole>().orderByAsc(DpRole::getId));
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<DpAdminRoleVO> out = new ArrayList<>();
        for (DpRole role : roles) {
            DpAdminRoleVO vo = new DpAdminRoleVO();
            vo.setId(role.getId());
            vo.setCode(role.getCode());
            vo.setName(role.getName());
            List<Long> permissionIds = dpRolePermissionMapper.selectPermissionIdsByRoleId(role.getId());
            vo.setPermissionIds(permissionIds == null ? new ArrayList<>() : new ArrayList<>(permissionIds));
            out.add(vo);
        }
        return out;
    }

    @Override
    public List<DpAdminPermissionVO> listPermissions() {
        List<DpPermission> permissions = dpPermissionMapper.selectList(
                new LambdaQueryWrapper<DpPermission>().orderByAsc(DpPermission::getId));
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyList();
        }
        List<DpAdminPermissionVO> out = new ArrayList<>();
        for (DpPermission permission : permissions) {
            DpAdminPermissionVO vo = new DpAdminPermissionVO();
            vo.setId(permission.getId());
            vo.setCode(permission.getCode());
            vo.setName(permission.getName());
            out.add(vo);
        }
        return out;
    }

    @Override
    @Transactional
    public void replaceRolePermissions(long roleId, List<Long> permissionIds) {
        if (roleId <= 0) {
            throw new IllegalArgumentException("roleId 无效");
        }
        DpRole role = dpRoleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        dpRolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds != null) {
            for (Long permissionId : permissionIds) {
                if (permissionId == null || permissionId <= 0) {
                    continue;
                }
                DpPermission permission = dpPermissionMapper.selectById(permissionId);
                if (permission != null) {
                    dpRolePermissionMapper.insert(roleId, permissionId);
                }
            }
        }
        dpPermissionService.evictAll();
    }

    @Override
    public DpAdminUserPageVO listUsers(int page, int size, String keyword) {
        int safePage = page <= 0 ? 1 : page;
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        String kw = keyword == null ? "" : keyword.trim();

        PageHelper.startPage(safePage, safeSize);
        List<DpUser> users = dpUserMapper.selectUsersForAdmin(kw.isEmpty() ? null : kw);
        PageInfo<DpUser> pageInfo = new PageInfo<>(users);

        DpAdminUserPageVO out = new DpAdminUserPageVO();
        out.setTotal(pageInfo.getTotal());
        List<DpAdminUserVO> list = new ArrayList<>();
        if (users != null) {
            for (DpUser user : users) {
                DpAdminUserVO vo = new DpAdminUserVO();
                vo.setId(user.getId());
                vo.setNickname(user.getNickname());
                List<Long> roleIds = dpUserRoleMapper.selectRoleIdsByUserId(user.getId());
                vo.setRoleIds(roleIds == null ? new ArrayList<>() : new ArrayList<>(roleIds));
                list.add(vo);
            }
        }
        out.setList(list);
        return out;
    }

    @Override
    @Transactional
    public void replaceUserRoles(int userId, List<Long> roleIds) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 无效");
        }
        DpUser user = dpUserMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        dpUserRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId == null || roleId <= 0) {
                    continue;
                }
                DpRole role = dpRoleMapper.selectById(roleId);
                if (role != null) {
                    dpUserRoleMapper.insert(userId, roleId);
                }
            }
        }
        dpPermissionService.evictUser(userId);
    }
}
