package com.example.mgdemoplus.user;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
import com.example.mgdemoplus.user.dto.DpUserProfileView;

public interface DpUserService {
    int registerUser(DpUser dpUser);

    DpUser selectById(int id);

    DpUserProfileView buildProfileView(DpUser user);

    String loginUser(String nickname, String password);

    DpUser loginUserOrNull(String nickname, String password);

    /**
     * 修改昵称和/或密码；须校验当前密码。昵称变更时由调用方刷新 JWT。
     */
    DpUserProfileUpdateResult updateProfile(DpUser current, DpUserProfileUpdateRequest request);
}
