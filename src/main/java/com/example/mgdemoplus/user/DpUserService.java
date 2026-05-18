package com.example.mgdemoplus.user;

import com.example.mgdemoplus.common.entity.DpUser;

public interface DpUserService {
    int registerUser(DpUser dpUser);

    DpUser selectById(int id);

    String loginUser(String nickname, String password);

    /**
     * ???????????? id?????? null?????????????????????
     */
    DpUser loginUserOrNull(String nickname, String password);
    String updateUserInfo(DpUser dpUser,String oldPassword);
}
