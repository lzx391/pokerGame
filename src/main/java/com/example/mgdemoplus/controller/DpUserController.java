package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.rbac.DpPermissionService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.user.cache.DpRedisLoginCacheService;
import com.example.mgdemoplus.user.DpUserService;
import com.example.mgdemoplus.user.impl.DpUserServiceImpl;
import com.example.mgdemoplus.user.dto.DpUserPasswordUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
import com.example.mgdemoplus.user.dto.DpPlayerHonorView;
import com.example.mgdemoplus.user.dto.DpUserProfileView;
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
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.achievement.vo.DpAchievementWallItemVO;
import com.example.mgdemoplus.user.dto.DpAvatarUploadResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/dpUser")
public class DpUserController {
    @Autowired
    DpUserService dpUserService;
    @Autowired
    DpUserMapper dpUserMapper;
    @Autowired
    DpRedisLoginCacheService dpRedisLoginCacheService;
    @Autowired
    JwtTokenService jwtTokenService;
    @Autowired
    DpAchievementService dpAchievementService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DpCurrentUserSupport currentUserSupport;
    @Autowired
    DpPermissionService dpPermissionService;

    @PostMapping("/registerUser")
    public ResultUtil registerUser(@RequestBody DpUser dpUser) {
        int code = dpUserService.registerUser(dpUser);
        if (code == DpUserServiceImpl.REGISTER_OK) {
            String jti = UUID.randomUUID().toString();
            String token = jwtTokenService.generateToken(dpUser.getNickname(), jti);
            dpRedisLoginCacheService.setLoginJti(dpUser.getNickname(), jti);
            return ResultUtil.ok()
                    .data("message", "注册成功")
                    .data("userId", dpUser.getId())
                    .data("nickname", dpUser.getNickname())
                    .data("token", token);
        }
        if (code == DpUserServiceImpl.REGISTER_SENSITIVE) {
            return ResultUtil.sensitiveUsername();
        }
        if (code == DpUserServiceImpl.REGISTER_INVALID_NICKNAME) {
            return ResultUtil.invalidNickname();
        }
        if (code == DpUserServiceImpl.REGISTER_NUMERIC_NICKNAME) {
            return ResultUtil.numericNickname();
        }
        return ResultUtil.repeatUsername();
    }

    @GetMapping("/loginProfile")
    public ResultUtil loginProfile(@RequestParam String nickname, @RequestParam String password) {
        DpUser u = dpUserService.loginUserOrNull(nickname, password);
        if (u == null) {
            return ResultUtil.error().data("message", "用户名不存在或密码错误");
        }
        String jti = UUID.randomUUID().toString();
        String token = jwtTokenService.generateToken(u.getNickname(), jti);
        dpRedisLoginCacheService.setLoginJti(u.getNickname(), jti);
        return ResultUtil.ok().data("userId", u.getId()).data("nickname", u.getNickname()).data("token", token);
    }

    /**
     * 当前登录用户的 RBAC 权限码列表。
     */
    @GetMapping("/permissions")
    public ResultUtil currentUserPermissions() {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        Set<String> permissions = dpPermissionService.resolveByUserId(userId);
        return ResultUtil.ok().data("permissions", new ArrayList<>(permissions));
    }

    /**
     * 获取当前登录用户资料（不含密码哈希）。身份仅从 JWT 解析，不信任客户端传的 id。
     */
    @GetMapping("/profile")
    public ResultUtil getProfile() {
        DpUser current = currentUserSupport.requireUser();
        if (current == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        DpUserProfileView view = dpUserService.buildProfileView(current);
        return ResultUtil.ok().data("profile", view);
    }

    /**
     * 查询指定用户的公开荣誉战绩（局内点击玩家卡片调用）。
     */
    @GetMapping("/stats/{userId}")
    public ResultUtil getPublicStats(@PathVariable int userId) {
        DpPlayerHonorView view = dpUserService.buildHonorView(userId);
        if (view == null) {
            return ResultUtil.error().data("message", "用户不存在");
        }
        return ResultUtil.ok().data("honor", view);
    }

    /**
     * 修改非敏感资料（当前仅昵称）。改密请使用 {@code PUT /dpUser/password}。
     * 改昵称成功后签发新 token 并迁移 Redis jti。
     */
    @PutMapping("/profile")
    public ResultUtil updateProfile(@RequestBody JsonNode body) {
        DpUser current = currentUserSupport.requireUser();
        if (current == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        if (hasNonBlankJsonField(body, "newPassword") || hasNonBlankJsonField(body, "oldPassword")) {
            return ResultUtil.error().data("message", DpUserServiceImpl.MSG_USE_PASSWORD_ENDPOINT);
        }
        DpUserProfileUpdateRequest request;
        try {
            request = objectMapper.treeToValue(body, DpUserProfileUpdateRequest.class);
        } catch (Exception e) {
            return ResultUtil.error().data("message", "参数无效");
        }
        String oldNickname = current.getNickname();
        DpUserProfileUpdateResult outcome = dpUserService.updateProfile(current, request);
        if (DpUserServiceImpl.MSG_SENSITIVE.equals(outcome.getMessage())) {
            return ResultUtil.sensitiveUsername();
        }
        if (!"保存成功".equals(outcome.getMessage())) {
            return ResultUtil.error().data("message", outcome.getMessage());
        }
        ResultUtil ok = ResultUtil.ok().data("message", outcome.getMessage());
        if (outcome.isNicknameChanged()) {
            String jti = UUID.randomUUID().toString();
            String token = jwtTokenService.generateToken(outcome.getNickname(), jti);
            dpRedisLoginCacheService.removeLoginJti(oldNickname);
            dpRedisLoginCacheService.setLoginJti(outcome.getNickname(), jti);
            ok.data("nicknameChanged", true)
                    .data("nickname", outcome.getNickname())
                    .data("token", token);
        }
        return ok;
    }

    /**
     * 修改或首次设置登录密码。已设密用户须 oldPassword；OAuth 无密码用户仅 newPassword。不换 JWT。
     */
    @PutMapping("/password")
    public ResultUtil updatePassword(@RequestBody DpUserPasswordUpdateRequest request) {
        DpUser current = currentUserSupport.requireUser();
        if (current == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        String message = dpUserService.updatePassword(current, request);
        if (!"保存成功".equals(message)) {
            return ResultUtil.error().data("message", message);
        }
        return ResultUtil.ok().data("message", message);
    }

    /**
     * 上传头像：身份仅从 JWT 解析，禁止客户端指定 userId。
     */
    @PostMapping("/avatar")
    public ResultUtil uploadAvatar(@RequestParam("file") MultipartFile file) {
        DpUser current = currentUserSupport.requireUser();
        if (current == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        DpAvatarUploadResult outcome = dpUserService.uploadAvatar(current, file);
        if (!outcome.isSuccess()) {
            return ResultUtil.error().data("message", outcome.getMessage());
        }
        return ResultUtil.ok()
                .data("message", outcome.getMessage())
                .data("avatarUrl", outcome.getAvatarUrl())
                .data("avatarUpdatedAt", outcome.getAvatarUpdatedAt());
    }

    /**
     * 当前登录用户成就墙（全部成就 + 本人解锁状态）。
     */
    @GetMapping("/achievements")
    public ResultUtil getMyAchievements() {
        DpUser current = currentUserSupport.requireUser();
        if (current == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
        }
        List<DpAchievementWallItemVO> items = dpAchievementService.buildWallForUser(current.getId());
        return ResultUtil.ok().data("achievements", items);
    }

    /**
     * 指定用户公开成就墙（登录即可查看）。
     */
    @GetMapping("/achievements/{userId}")
    public ResultUtil getUserAchievements(@PathVariable int userId) {
        List<DpAchievementWallItemVO> items = dpAchievementService.buildWallForUser(userId);
        if (items == null) {
            return ResultUtil.error().data("message", "用户不存在");
        }
        return ResultUtil.ok().data("achievements", items);
    }

    private static boolean hasNonBlankJsonField(JsonNode body, String field) {
        if (body == null || !body.has(field)) {
            return false;
        }
        JsonNode node = body.get(field);
        return node != null && !node.isNull() && node.isTextual() && !node.asText().isBlank();
    }

}
