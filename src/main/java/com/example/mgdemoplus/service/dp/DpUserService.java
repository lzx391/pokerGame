package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.entity.dp.DpUser;

public interface DpUserService {
    int registerUser(DpUser dpUser);

    DpUser selectById(int id);

    String loginUser(String nickname, String password);

    /**
     * 登录成功返回用户实体（含 id）；失败返回 null。不用于返回给前端的密码字段由调用方忽略。
     */
    DpUser loginUserOrNull(String nickname, String password);
    String updateUserInfo(DpUser dpUser);
}
