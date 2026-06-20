package com.example.mgdemoplus.rbac.controller;

import com.example.mgdemoplus.rbac.DpRbacService;
import com.example.mgdemoplus.rbac.bo.DpAdminVerifyPasswordRequest;
import com.example.mgdemoplus.rbac.bo.DpRolePermissionsUpdateRequest;
import com.example.mgdemoplus.rbac.bo.DpUserRolesUpdateRequest;
import com.example.mgdemoplus.room.support.DpExperimentalDeckPresetPasswordGuard;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dp/admin")
public class DpAdminRbacController {

    @Autowired
    private DpCurrentUserSupport currentUserSupport;
    @Autowired
    private DpExperimentalDeckPresetPasswordGuard passwordGuard;
    @Autowired
    private DpRbacService dpRbacService;

    @PostMapping("/verifyPassword")
    public ResultUtil verifyPassword(@RequestBody DpAdminVerifyPasswordRequest body) {
        if (currentUserSupport.requireNickname() == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        String password = body != null ? body.getPassword() : null;
        if (!passwordGuard.verify(password)) {
            return ResultUtil.error().data("message", "密码错误");
        }
        return ResultUtil.ok().data("verified", true);
    }

    @GetMapping("/roles")
    public ResultUtil listRoles() {
        if (currentUserSupport.requireNickname() == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        return ResultUtil.ok().data("list", dpRbacService.listRolesWithPermissions());
    }

    @GetMapping("/permissions")
    public ResultUtil listPermissions() {
        if (currentUserSupport.requireNickname() == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        return ResultUtil.ok().data("list", dpRbacService.listPermissions());
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ResultUtil replaceRolePermissions(
            @PathVariable("roleId") long roleId,
            @RequestBody DpRolePermissionsUpdateRequest body) {
        if (currentUserSupport.requireNickname() == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        try {
            dpRbacService.replaceRolePermissions(roleId, body != null ? body.getPermissionIds() : null);
            return ResultUtil.ok().data("message", "保存成功");
        } catch (IllegalArgumentException ex) {
            return ResultUtil.error().data("message", ex.getMessage());
        }
    }
/**
 * 查用户的权限们
 * @param page
 * @param size
 * @param keyword
 * @return
 */
    @GetMapping("/users")
    public ResultUtil listUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        if (currentUserSupport.requireNickname() == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        var pageResult = dpRbacService.listUsers(page, size, keyword);
        return ResultUtil.ok().data("list", pageResult.getList()).data("total", pageResult.getTotal());
    }

    @PutMapping("/users/{userId}/roles")
    public ResultUtil replaceUserRoles(
            @PathVariable("userId") int userId,
            @RequestBody DpUserRolesUpdateRequest body) {
        if (currentUserSupport.requireNickname() == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        try {
            dpRbacService.replaceUserRoles(userId, body != null ? body.getRoleIds() : null);
            return ResultUtil.ok().data("message", "保存成功");
        } catch (IllegalArgumentException ex) {
            return ResultUtil.error().data("message", ex.getMessage());
        }
    }
}
