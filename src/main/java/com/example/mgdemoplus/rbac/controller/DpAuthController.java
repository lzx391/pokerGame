package com.example.mgdemoplus.rbac.controller;

import com.example.mgdemoplus.rbac.DpPermissionService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Set;

@RestController
@RequestMapping("/dp/auth")
public class DpAuthController {

    @Autowired
    private DpCurrentUserSupport currentUserSupport;
    @Autowired
    private DpPermissionService dpPermissionService;

    @GetMapping("/permissions")
    public ResultUtil currentUserPermissions() {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        Set<String> permissions = dpPermissionService.resolveByUserId(userId);
        return ResultUtil.ok().data("permissions", new ArrayList<>(permissions));
    }
}
