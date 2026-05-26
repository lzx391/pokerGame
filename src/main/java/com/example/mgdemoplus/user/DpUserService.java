package com.example.mgdemoplus.user;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.user.dto.DpAvatarUploadResult;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
import com.example.mgdemoplus.user.dto.DpUserProfileView;
import com.example.mgdemoplus.user.dto.DpPlayerHonorView;

import org.springframework.web.multipart.MultipartFile;

public interface DpUserService {
    int registerUser(DpUser dpUser);

    DpUser selectById(int id);

    DpUserProfileView buildProfileView(DpUser user);

    DpUser loginUserOrNull(String nickname, String password);

    /**
     * 修改昵称和/或密码；须校验当前密码。昵称变更时由调用方刷新 JWT。
     */
    DpUserProfileUpdateResult updateProfile(DpUser current, DpUserProfileUpdateRequest request);

    /**
     * 根据 userId 查询公开荣誉战绩（局内其他玩家可查看）。
     */
    DpPlayerHonorView buildHonorView(int userId);

    /**
     * 上传/替换当前用户头像：写磁盘 {@code /images/{userId}.{ext}}，更新 {@code dp_user.avatar_url}，删除旧文件。
     */
    DpAvatarUploadResult uploadAvatar(DpUser current, MultipartFile file);
}
