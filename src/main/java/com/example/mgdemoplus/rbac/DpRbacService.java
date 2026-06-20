package com.example.mgdemoplus.rbac;

import com.example.mgdemoplus.rbac.vo.DpAdminPermissionVO;
import com.example.mgdemoplus.rbac.vo.DpAdminRoleVO;
import com.example.mgdemoplus.rbac.vo.DpAdminUserPageVO;

import java.util.List;

public interface DpRbacService {

    void bindPlayerRole(int userId);

    List<DpAdminRoleVO> listRolesWithPermissions();

    List<DpAdminPermissionVO> listPermissions();

    void replaceRolePermissions(long roleId, List<Long> permissionIds);

    DpAdminUserPageVO listUsers(int page, int size, String keyword);

    void replaceUserRoles(int userId, List<Long> roleIds);
}
