package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.example.mgdemoplus.service.DpUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DpUserServiceImpl implements DpUserService {
    @Autowired
    DpUserMapper dpUserMapper;

    public int registerUser(DpUser dpUser) {
        DpUser repetition = dpUserMapper.selectByNickname(dpUser.getNickname());//查是否重名
        if(repetition!=null){
            return 0;
        }
        return dpUserMapper.registerUser(dpUser);
    }

    public DpUser selectById(int id) {
        return dpUserMapper.selectById(id);
    }

    public String loginUser(String nickname, String password) {
        if (dpUserMapper.loginUser(nickname, password) == null) {
            return "登录失败";
        }
        return "登录成功";
    }
}
