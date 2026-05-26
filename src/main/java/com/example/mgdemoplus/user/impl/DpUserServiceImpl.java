package com.example.mgdemoplus.user.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.entity.DpUserStats;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.user.DpUserService;
import com.example.mgdemoplus.user.dto.DpAvatarUploadResult;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
import com.example.mgdemoplus.user.dto.DpPlayerHonorView;
import com.example.mgdemoplus.user.dto.DpUserProfileView;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.utils.CryptoUtil;
import com.example.mgdemoplus.utils.DpImageFileSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class DpUserServiceImpl implements DpUserService {
    @Autowired
    DpUserMapper dpUserMapper;
    @Autowired
    DpUserStatsMapper dpUserStatsMapper;
    @Autowired
    DpSensitiveWordService sensitiveWordService;

    @Value("${mgdemoplus.images.file-location:file:P:/javaworkspace/DPGameFiles/images/}")
    private String imagesFileLocation;

    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    /** 注册成功 */
    public static final int REGISTER_OK = 1;
    /** 昵称重复 */
    public static final int REGISTER_REPEAT = 0;
    /** 昵称含敏感词 */
    public static final int REGISTER_SENSITIVE = 2;
    /** 昵称超长 */
    public static final int REGISTER_INVALID_NICKNAME = 3;

    public static final String MSG_SENSITIVE = "敏感词汇";

    public int registerUser(DpUser dpUser) {
        if (dpUser.getNickname() == null || dpUser.getNickname().length() > 10) {
            return REGISTER_INVALID_NICKNAME;
        }
        if (sensitiveWordService.containsSensitive(dpUser.getNickname())) {
            return REGISTER_SENSITIVE;
        }
        DpUser repetition = dpUserMapper.selectByNickname(dpUser.getNickname());
        if (repetition != null) {
            return 0;
        }
        dpUser.setPassword(CryptoUtil.bcryptEncode(dpUser.getPassword()));
        return dpUserMapper.registerUser(dpUser);
    }

    public DpUser selectById(int id) {
        return dpUserMapper.selectById(id);
    }

    @Override
    public DpUserProfileView buildProfileView(DpUser user) {
        if (user == null) {
            return null;
        }
        DpUserProfileView view = new DpUserProfileView();
        view.setId(user.getId());
        view.setNickname(user.getNickname());
        view.setAvatarUrl(user.getAvatarUrl());
        view.setPasswordSet(user.getPassword() != null && !user.getPassword().isBlank());
        DpUserStats stats = dpUserStatsMapper.selectByUserId(user.getId());
        if (stats != null) {
            view.setRoyalFlushWins(stats.getRoyalFlushWins());
            view.setStraightFlushWins(stats.getStraightFlushWins());
            view.setFourOfAKindWins(stats.getFourOfAKindWins());
            view.setLargestPotWon(stats.getLargestPotWon());
            view.setLargestRoomNet(stats.getLargestRoomNet());
            view.setTotalHandsPlayed(stats.getTotalHandsPlayed());
        }
        return view;
    }

    @Override
    public DpPlayerHonorView buildHonorView(int userId) {
        DpUser user = dpUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        DpPlayerHonorView view = new DpPlayerHonorView();
        view.setUserId(user.getId());
        view.setNickname(user.getNickname());
        view.setAvatarUrl(user.getAvatarUrl());
        DpUserStats stats = dpUserStatsMapper.selectByUserId(userId);
        if (stats != null) {
            view.setRoyalFlushWins(stats.getRoyalFlushWins());
            view.setStraightFlushWins(stats.getStraightFlushWins());
            view.setFourOfAKindWins(stats.getFourOfAKindWins());
            view.setLargestPotWon(stats.getLargestPotWon());
            view.setLargestRoomNet(stats.getLargestRoomNet());
            view.setTotalHandsPlayed(stats.getTotalHandsPlayed());
        }
        return view;
    }

    public DpUser loginUserOrNull(String nickname, String password) {
        DpUser user = dpUserMapper.selectByNickname(nickname);
        if (user == null || !CryptoUtil.bcryptMatches(password, user.getPassword())) {
            return null;
        }
        return user;
    }

    @Override
    public DpUserProfileUpdateResult updateProfile(DpUser current, DpUserProfileUpdateRequest request) {
        DpUserProfileUpdateResult result = new DpUserProfileUpdateResult();
        if (current == null || request == null) {
            result.setMessage("参数无效");
            return result;
        }
        //验证旧密码是否输入
        String oldPassword = request.getOldPassword();
        if (oldPassword == null || oldPassword.isBlank()) {
            result.setMessage("请填写当前密码");
            return result;
        }
        //验证旧密码是否正确
        DpUser stored = dpUserMapper.selectById(current.getId());
        if (stored == null || !CryptoUtil.bcryptMatches(oldPassword, stored.getPassword())) {
            result.setMessage("当前密码错误");
            return result;
        }
        //验证昵称是否输入
        String newNickname = request.getNickname() != null ? request.getNickname().trim() : "";
        if (newNickname.isEmpty()) {
            result.setMessage("昵称不能为空");
            return result;
        }
        //验证昵称长度
        if (newNickname.length() > 10) {
            result.setMessage("昵称最多 10 个字符");
            return result;
        }
        if (sensitiveWordService.containsSensitive(newNickname)) {
            result.setMessage(MSG_SENSITIVE);
            return result;
        }

        boolean nicknameChanged = !newNickname.equals(stored.getNickname());
        if (nicknameChanged) {
            DpUser taken = dpUserMapper.selectByNickname(newNickname);
            if (taken != null && taken.getId() != stored.getId()) {
                result.setMessage("昵称已被占用");
                return result;
            }
            DpUser patch = new DpUser();
            patch.setId(stored.getId());
            patch.setNickname(newNickname);
            if (dpUserMapper.updateNickname(patch) != 1) {
                result.setMessage("昵称更新失败");
                return result;
            }
            result.setNicknameChanged(true);
            result.setNickname(newNickname);
        }

        String newPassword = request.getNewPassword();
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 6) {
                result.setMessage("新密码至少 6 位");
                return result;
            }
            DpUser patch = new DpUser();
            patch.setId(stored.getId());
            patch.setPassword(CryptoUtil.bcryptEncode(newPassword));
            if (dpUserMapper.updatePasswordHash(patch) != 1) {
                result.setMessage("密码更新失败");
                return result;
            }
        }

        if (!nicknameChanged && (newPassword == null || newPassword.isBlank())) {
            result.setMessage("没有需要保存的修改");
            return result;
        }

        result.setMessage("保存成功");
        return result;
    }

    @Override
    public DpAvatarUploadResult uploadAvatar(DpUser current, MultipartFile file) {
        if (current == null || current.getId() <= 0) {
            return DpAvatarUploadResult.fail("未登录或用户无效");
        }
        if (file == null || file.isEmpty()) {
            return DpAvatarUploadResult.fail("请选择图片文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            return DpAvatarUploadResult.fail("图片不能超过 2MB");
        }
        String ext = DpImageFileSupport.extensionOf(file.getOriginalFilename());
        if (!DpImageFileSupport.isAllowedImageExtension(ext)) {
            return DpAvatarUploadResult.fail("仅支持 jpg、png、webp、gif");
        }

        DpUser stored = dpUserMapper.selectById(current.getId());
        if (stored == null) {
            return DpAvatarUploadResult.fail("用户不存在");
        }

        String dir = DpImageFileSupport.toPhysicalDir(imagesFileLocation);
        File folder = new File(dir);
        if (!folder.exists() && !folder.mkdirs()) {
            return DpAvatarUploadResult.fail("无法创建图片目录");
        }

        String oldUrl = stored.getAvatarUrl();
        DpImageFileSupport.deleteWebPathFile(imagesFileLocation, oldUrl);
        DpImageFileSupport.deleteUserAvatarFiles(imagesFileLocation, stored.getId());

        String storedFilename = stored.getId() + ext;
        String webPath = "/images/" + storedFilename;
        try {
            file.transferTo(new File(dir, storedFilename));
        } catch (IOException e) {
            return DpAvatarUploadResult.fail("保存文件失败");
        }

        if (dpUserMapper.updateAvatarUrl(stored.getId(), webPath) != 1) {
            DpImageFileSupport.deleteWebPathFile(imagesFileLocation, webPath);
            return DpAvatarUploadResult.fail("更新资料失败");
        }

        return DpAvatarUploadResult.ok(webPath);
    }
}
