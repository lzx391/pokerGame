package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.example.mgdemoplus.service.dp.DpUserService;
import com.example.mgdemoplus.utils.CryptoUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DpUserServiceImpl implements DpUserService {
    @Autowired
    DpUserMapper dpUserMapper;

    public int registerUser(DpUser dpUser) {
        //这里添加一个功能，防止用户名注册含有“海金”的昵称
        if(dpUser.getNickname().contains("海金")){
            return 2;
        }
        DpUser repetition = dpUserMapper.selectByNickname(dpUser.getNickname());//查是否重名
        if(repetition!=null){
            return 0;
        }
        dpUser.setPassword(CryptoUtil.md5HexUtf8(dpUser.getPassword()));
        return dpUserMapper.registerUser(dpUser);
    }

    public DpUser selectById(int id) {
        return dpUserMapper.selectById(id);
    }

    public String loginUser(String nickname, String password) {
        String md5Password = CryptoUtil.md5HexUtf8(password);
        if (dpUserMapper.loginUser(nickname, md5Password) == null) {
            return "登录失败";
        }
        return "登录成功";
    }

    public DpUser loginUserOrNull(String nickname, String password) {
        return dpUserMapper.loginUser(nickname, CryptoUtil.md5HexUtf8(password));
    }
    
    public String updateUserInfo(DpUser dpUser) {
        String md5Password = CryptoUtil.md5HexUtf8(dpUser.getPassword());
        DpUser user = dpUserMapper.selectById(dpUser.getId());
        if(!md5Password.equals(user.getPassword())){
            return "密码错误";
        }
        if(dpUserMapper.updateUserInfo(dpUser)==1){
            return "更新成功";
        }
        return "更新失败";
    }
}
