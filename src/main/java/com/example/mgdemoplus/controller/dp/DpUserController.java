package com.example.mgdemoplus.controller.dp;

import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.service.DpUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/dpUser")
public class DpUserController {
    @Autowired
    DpUserService dpUserService;

    @PostMapping("/registerUser")
    public String registerUser(@RequestBody DpUser dpUser) {
        if (dpUserService.registerUser(dpUser) == 1) {
            return "注册成功";
        }else if(dpUserService.registerUser(dpUser) == 2){
            return "注册失败:用户名含有“海金”";
        }
        System.out.println("用户名重复");
        return "注册失败:用户名重复";
    }
    @GetMapping("/loginUser")
    public String loginUser(@RequestParam String nickname,@RequestParam String password){
        System.out.println("用户："+nickname+"正在登录");
         return dpUserService.loginUser(nickname,password);
    }

    /**
     * 登录并返回 userId（供牌谱关联等使用）；成功 ok=true，失败 ok=false。
     * 与 {@link #loginUser} 并存，前端可改用本接口以拿到数字 id（界面仍只展示昵称）。
     */
    @GetMapping("/loginProfile")
    public Map<String, Object> loginProfile(@RequestParam String nickname, @RequestParam String password) {
        DpUser u = dpUserService.loginUserOrNull(nickname, password);
        Map<String, Object> m = new LinkedHashMap<>();
        if (u == null) {
            m.put("ok", false);
            m.put("message", "登录失败");
            return m;
        }
        m.put("ok", true);
        m.put("userId", u.getId());
        m.put("nickname", u.getNickname());
        return m;
    }

}
