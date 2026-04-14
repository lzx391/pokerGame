package com.example.mgdemoplus.controller.dp;

import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.security.JwtSecurityConstants;
import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.service.dp.DpRedisLoginCacheService;
import com.example.mgdemoplus.service.dp.DpUserService;
import com.example.mgdemoplus.utils.ResultUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/dpUser")
public class DpUserController {
    @Autowired
    DpUserService dpUserService;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    DpRedisLoginCacheService dpRedisLoginCacheService;
    @Autowired
    JwtTokenService jwtTokenService;
    @PostMapping("/registerUser")
    public ResultUtil registerUser(@RequestBody DpUser dpUser) {
        
        if (dpUserService.registerUser(dpUser) == 1) {
            return ResultUtil.ok().data("message", "注册成功");
        } else if (dpUserService.registerUser(dpUser) == 2) {
            return ResultUtil.sensitiveUsername();
        }
        return ResultUtil.repeatUsername();
    }

    @GetMapping("/loginUser")
    public String loginUser(@RequestParam String nickname, @RequestParam String password) {
        System.out.println("用户：" + nickname + "正在登录");
        return dpUserService.loginUser(nickname, password);
    }

    /**
     * 登录并返回 userId（供牌谱关联等使用）；成功 ok=true，失败 ok=false。
     * 与 {@link #loginUser} 并存，前端可改用本接口以拿到数字 id（界面仍只展示昵称）。
     */
    // @GetMapping("/loginProfile")
    // public Map<String, Object> loginProfile(@RequestParam String nickname,
    // @RequestParam String password) {
    // DpUser u = dpUserService.loginUserOrNull(nickname, password);
    // Map<String, Object> m = new LinkedHashMap<>();
    // if (u == null) {
    // m.put("ok", false);
    // m.put("message", "登录失败");
    // return m;
    // }
    // m.put("ok", true);
    // m.put("userId", u.getId());
    // m.put("nickname", u.getNickname());
    // return m;
    // }
    @GetMapping("/loginProfile")
    public ResultUtil loginProfile(@RequestParam String nickname, @RequestParam String password) {
        DpUser u = dpUserService.loginUserOrNull(nickname, password);
        if (u == null) {
            return ResultUtil.error().data("message", "用户名不存在或密码错误");
        }
        //token载荷有昵称和jti,jti是随机生成的uuid，到时候踢人用
        String jti = UUID.randomUUID().toString();
        String token = jwtTokenService.generateToken(u.getNickname(), jti);
        dpRedisLoginCacheService.setLoginJti(u.getNickname(), jti);
        return ResultUtil.ok().data("userId", u.getId()).data("nickname", u.getNickname()).data("token", token);
    }

  
    @PutMapping("/updateUserInfo")
    public ResultUtil updateUserInfo(@RequestBody DpUser dpUser,String oldPassword) {
        //TODO: process POST request
        
        return ResultUtil.ok().data("message", dpUserService.updateUserInfo(dpUser,oldPassword));
    }
    
}
