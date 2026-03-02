package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.entity.DpUser;
import com.example.mgdemoplus.service.DpUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dpUser")
public class DpUserController {
    @Autowired
    DpUserService dpUserService;

    @PostMapping("/registerUser")
    public String registerUser(@RequestBody DpUser dpUser) {
        if (dpUserService.registerUser(dpUser) == 1) {
            return "注册成功";
        }
        System.out.println("用户名重复");
        return "注册失败:用户名重复";
    }
    @GetMapping("/loginUser")
    public String loginUser(@RequestParam String nickname,@RequestParam String password){
        System.out.println("用户："+nickname+"正在登录");
         return dpUserService.loginUser(nickname,password);
    }

}
