package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.user.cache.DpRedisLoginCacheService;
import com.example.mgdemoplus.user.DpUserService;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
import com.example.mgdemoplus.user.dto.DpUserProfileView;
import com.example.mgdemoplus.utils.ResultUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        if (code == 1) {
            String jti = UUID.randomUUID().toString();
            String token = jwtTokenService.generateToken(dpUser.getNickname(), jti);
            dpRedisLoginCacheService.setLoginJti(dpUser.getNickname(), jti);
            return ResultUtil.ok()
                    .data("message", "注册成功")
                    .data("userId", dpUser.getId())
                    .data("nickname", dpUser.getNickname())
                    .data("token", token);
        }
        return ResultUtil.repeatUsername();
    }

    @GetMapping("/loginUser")
    public String loginUser(@RequestParam String nickname, @RequestParam String password) {
        System.out.println("用户：" + nickname + "正在登录");
        return dpUserService.loginUser(nickname, password);
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
     * 当前登录用户资料（不含密码哈希）。身份仅从 JWT 解析，不信任客户端传的 id。
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

    private DpUser requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return null;
        }
        return dpUserMapper.selectByNickname(auth.getName());
    }
}
