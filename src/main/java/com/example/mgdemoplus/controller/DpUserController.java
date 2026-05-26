package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.user.cache.DpRedisLoginCacheService;
import com.example.mgdemoplus.user.DpUserService;
import com.example.mgdemoplus.user.impl.DpUserServiceImpl;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
import com.example.mgdemoplus.user.dto.DpPlayerHonorView;
import com.example.mgdemoplus.user.dto.DpUserProfileView;
import com.example.mgdemoplus.utils.ResultUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.mgdemoplus.user.dto.DpAvatarUploadResult;

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
     * 获取当前登录用户资料（不含密码哈希）。身份仅从 JWT 解析，不信任客户端传的 id。
     */
    @GetMapping("/profile")
    public ResultUtil getProfile() {
        DpUser current = requireCurrentUser();
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
     * 修改昵称和/或密码。保存时须校验当前密码；改昵称后签发新 token（JWT subject 为昵称）。
     */
    @PutMapping("/profile")
    public ResultUtil updateProfile(@RequestBody DpUserProfileUpdateRequest request) {
        DpUser current = requireCurrentUser();
        if (current == null) {
            return ResultUtil.error().data("message", "未登录或登录已失效");
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
     * 上传头像：身份仅从 JWT 解析，禁止客户端指定 userId。
     */
    @PostMapping("/avatar")
    public ResultUtil uploadAvatar(@RequestParam("file") MultipartFile file) {
        DpUser current = requireCurrentUser();
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
     * 解析当前登录用户
     */
    private DpUser requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return null;
        }
        return dpUserMapper.selectByNickname(auth.getName());
    }
}
