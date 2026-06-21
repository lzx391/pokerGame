package com.example.mgdemoplus.user.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.entity.DpUserStats;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.user.DpUserService;
import com.example.mgdemoplus.user.dto.DpAvatarUploadResult;
import com.example.mgdemoplus.user.dto.DpUserPasswordUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateRequest;
import com.example.mgdemoplus.user.dto.DpUserProfileUpdateResult;
import com.example.mgdemoplus.user.dto.DpPlayerHonorView;
import com.example.mgdemoplus.user.dto.DpUserProfileView;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyReadService;
import com.example.mgdemoplus.rbac.DpRbacService;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.utils.CryptoUtil;
import com.example.mgdemoplus.storage.DpAvatarStorageSupport;
import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.DpWebPathSupport;
import com.example.mgdemoplus.utils.DpAvatarThumbnailSupport;
import com.example.mgdemoplus.utils.DpDateTimeSupport;
import com.example.mgdemoplus.utils.DpImageFileSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
public class DpUserServiceImpl implements DpUserService {
    // @Autowired
    // DpUserMapper dpUserMapper;
    // @Autowired
    // DpUserStatsMapper dpUserStatsMapper;
    // @Autowired
    // DpSensitiveWordService sensitiveWordService;
    // @Autowired
    // DpLeaderboardWeeklyReadService dpLeaderboardWeeklyReadService;
    // @Autowired
    // DpObjectStorage objectStorage;
    // @Autowired
    // DpAvatarStorageSupport avatarStorageSupport;
    // @Autowired
    // DpRbacService dpRbacService;

    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    /** 注册成功 */
    public static final int REGISTER_OK = 1;
    /** 昵称重复 */
    public static final int REGISTER_REPEAT = 0;
    /** 昵称含敏感词 */
    public static final int REGISTER_SENSITIVE = 2;
    /** 昵称超长 */
    public static final int REGISTER_INVALID_NICKNAME = 3;
    /** 昵称为纯数字 */
    public static final int REGISTER_NUMERIC_NICKNAME = 4;

    public static final String MSG_SENSITIVE = "敏感词汇";
    public static final String MSG_NUMERIC_NICKNAME = "昵称不能为纯数字";
    public static final String MSG_USE_PASSWORD_ENDPOINT = "请使用改密接口";
    private final DpUserMapper dpUserMapper;
    private final DpUserStatsMapper dpUserStatsMapper;
    private final DpSensitiveWordService sensitiveWordService;
    private final DpLeaderboardWeeklyReadService dpLeaderboardWeeklyReadService;
    private final DpObjectStorage objectStorage;
    private final DpAvatarStorageSupport avatarStorageSupport;
    private final DpRbacService dpRbacService;

    public DpUserServiceImpl(DpUserMapper dpUserMapper, DpUserStatsMapper dpUserStatsMapper,
            DpSensitiveWordService sensitiveWordService, DpLeaderboardWeeklyReadService dpLeaderboardWeeklyReadService,
            DpObjectStorage objectStorage, DpAvatarStorageSupport avatarStorageSupport, DpRbacService dpRbacService) {
        this.dpUserMapper = dpUserMapper;
        this.dpUserStatsMapper = dpUserStatsMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.dpLeaderboardWeeklyReadService = dpLeaderboardWeeklyReadService;
        this.objectStorage = objectStorage;
        this.avatarStorageSupport = avatarStorageSupport;
        this.dpRbacService = dpRbacService;
    }

    public int registerUser(DpUser dpUser) {
        String nickname = dpUser.getNickname() == null ? "" : dpUser.getNickname().trim();
        if (nickname.matches("\\d+")) {
            return REGISTER_NUMERIC_NICKNAME;
        }
        if (nickname.isEmpty() || nickname.length() > 10) {
            return REGISTER_INVALID_NICKNAME;
        }
        dpUser.setNickname(nickname);
        if (sensitiveWordService.containsSensitive(nickname)) {
            return REGISTER_SENSITIVE;
        }
        DpUser repetition = dpUserMapper.selectByNickname(nickname);
        if (repetition != null) {
            return 0;
        }
        dpUser.setPassword(CryptoUtil.bcryptEncode(dpUser.getPassword()));
        int result = dpUserMapper.registerUser(dpUser);
        if (result == REGISTER_OK) {
            dpRbacService.bindPlayerRole(dpUser.getId());
        }
        return result;
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
        view.setAvatarUpdatedAt(DpDateTimeSupport.toEpochMilli(user.getAvatarUpdatedAt()));
        view.setPasswordSet(user.getPassword() != null && !user.getPassword().isBlank());
        DpUserStats stats = dpUserStatsMapper.selectByUserId(user.getId());
        if (stats != null) {
            applyStatsToProfileView(view, stats);
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
        view.setAvatarUpdatedAt(DpDateTimeSupport.toEpochMilli(user.getAvatarUpdatedAt()));
        DpUserStats stats = dpUserStatsMapper.selectByUserId(userId);
        if (stats != null) {
            applyStatsToHonorView(view, stats);
        }
        view.setLeaderboardWeeklyHand(dpLeaderboardWeeklyReadService.placementForHand(userId));
        view.setLeaderboardWeeklyRoom(dpLeaderboardWeeklyReadService.placementForRoom(userId));
        return view;
    }

    private static void applyStatsToProfileView(DpUserProfileView view, DpUserStats stats) {
        view.setRoyalFlushWins(stats.getRoyalFlushWins());
        view.setStraightFlushWins(stats.getStraightFlushWins());
        view.setFourOfAKindWins(aggregateFourPlusWins(stats));
        view.setLargestPotWon(stats.getLargestPotWon());
        view.setLargestRoomNet(stats.getLargestRoomNet());
        view.setTotalHandsPlayed(stats.getTotalHandsPlayed());
        view.setLeaderboardTopCount(stats.getLeaderboardTopCount());
        view.setMaxWinStreak(stats.getMaxWinStreak());
    }

    private static void applyStatsToHonorView(DpPlayerHonorView view, DpUserStats stats) {
        view.setRoyalFlushWins(stats.getRoyalFlushWins());
        view.setStraightFlushWins(stats.getStraightFlushWins());
        view.setFourOfAKindWins(aggregateFourPlusWins(stats));
        view.setLargestPotWon(stats.getLargestPotWon());
        view.setLargestRoomNet(stats.getLargestRoomNet());
        view.setTotalHandsPlayed(stats.getTotalHandsPlayed());
        view.setLeaderboardTopCount(stats.getLeaderboardTopCount());
        view.setMaxWinStreak(stats.getMaxWinStreak());
    }

    /** 四条及以上牌力 = 四条 + 同花顺 + 皇家同花顺 */
    private static int aggregateFourPlusWins(DpUserStats stats) {
        return stats.getFourOfAKindWins() + stats.getStraightFlushWins() + stats.getRoyalFlushWins();
    }

    public DpUser loginUserOrNull(String nickname, String password) {
        DpUser user = dpUserMapper.selectByNickname(nickname);
        if (user == null) {
            return null;
        }
        // OAuth 用户无本地密码
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return null;
        }
        if (!CryptoUtil.bcryptMatches(password, user.getPassword())) {
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
        DpUser stored = dpUserMapper.selectById(current.getId());
        if (stored == null) {
            result.setMessage("用户不存在");
            return result;
        }

        if (request.getNickname() == null) {
            result.setMessage("没有需要保存的修改");
            return result;
        }

        String nicknameError = validateNicknameUpdate(stored, request.getNickname().trim(), result);
        if (nicknameError != null) {
            result.setMessage(nicknameError);
            return result;
        }

        if (!result.isNicknameChanged()) {
            result.setMessage("没有需要保存的修改");
            return result;
        }

        DpUser patch = new DpUser();
        patch.setId(stored.getId());
        patch.setNickname(result.getNickname());
        if (dpUserMapper.updateNickname(patch) != 1) {
            result.setNicknameChanged(false);
            result.setNickname(null);
            result.setMessage("昵称更新失败");
            return result;
        }

        result.setMessage("保存成功");
        return result;
    }

    @Override
    public String updatePassword(DpUser current, DpUserPasswordUpdateRequest request) {
        if (current == null || request == null) {
            return "参数无效";
        }
        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.isBlank()) {
            return "请填写新密码";
        }

        DpUser stored = dpUserMapper.selectById(current.getId());
        if (stored == null) {
            return "用户不存在";
        }
        // 有无旧密码？
        boolean hasPassword = stored.getPassword() != null && !stored.getPassword().isBlank();
        // 输入旧密码
        String oldPassword = request.getOldPassword();
        // 旧密码是否存在
        boolean hasOldPasswordInRequest = oldPassword != null && !oldPassword.isBlank();
        // 没有密码但是输入了旧密码
        if (!hasPassword && hasOldPasswordInRequest) {
            return "尚未设置密码，无需填写当前密码";
        }
        // 校验，如果有旧密码就验证旧密码对不对，没有旧密码通过，旧密码正确通过，然后出来直接设置新密码
        String passwordError = validatePasswordUpdate(stored, hasPassword, oldPassword, newPassword);
        if (passwordError != null) {
            return passwordError;
        }

        DpUser patch = new DpUser();
        patch.setId(stored.getId());
        patch.setPassword(CryptoUtil.bcryptEncode(newPassword));
        if (dpUserMapper.updatePasswordHash(patch) != 1) {
            return "密码更新失败";
        }
        return "保存成功";
    }

    /** 仅改昵称路径：不校验旧密码；oldPassword 传了也忽略。 */
    private String validateNicknameUpdate(DpUser stored, String newNickname, DpUserProfileUpdateResult result) {
        if (newNickname.isEmpty()) {
            return "昵称不能为空";
        }
        if (newNickname.length() > 10) {
            return "昵称最多 10 个字符";
        }
        if (newNickname.matches("\\d+")) {
            return MSG_NUMERIC_NICKNAME;
        }
        if (sensitiveWordService.containsSensitive(newNickname)) {
            return MSG_SENSITIVE;
        }
        if (newNickname.equals(stored.getNickname())) {
            return null;
        }
        DpUser taken = dpUserMapper.selectByNickname(newNickname);
        if (taken != null && taken.getId() != stored.getId()) {
            return "昵称已被占用";
        }
        result.setNicknameChanged(true);
        result.setNickname(newNickname);
        return null;
    }

    /** 改密 / 首次设密路径：与昵称逻辑分离，仅校验不写库。 */
    private String validatePasswordUpdate(DpUser stored, boolean hasPassword, String oldPassword, String newPassword) {
        if (newPassword.length() < 6) {
            return "新密码至少 6 位";
        }
        if (hasPassword) {
            if (oldPassword == null || oldPassword.isBlank()) {
                return "请填写当前密码";
            }
            if (!CryptoUtil.bcryptMatches(oldPassword, stored.getPassword())) {
                return "当前密码错误";
            }
        }
        return null;
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

        String oldUrl = stored.getAvatarUrl();
        avatarStorageSupport.deleteWebPathFile(oldUrl);
        avatarStorageSupport.deleteUserAvatarFiles(stored.getId());

        String storedFilename = stored.getId() + ext;
        String webPath = DpWebPathSupport.IMAGES_PREFIX + storedFilename;
        Path tempOriginal = null;
        try {
            tempOriginal = Files.createTempFile("avatar-" + stored.getId() + "-", ext);
            file.transferTo(tempOriginal);
            String contentType = DpWebPathSupport.guessContentType(webPath);
            objectStorage.put(webPath, Files.newInputStream(tempOriginal), Files.size(tempOriginal), contentType);
            DpAvatarThumbnailSupport.generateThumbnailBytes(tempOriginal.toFile()).ifPresent(thumbBytes -> {
                try {
                    String thumbPath = DpImageFileSupport.avatarThumbWebPath(stored.getId());
                    objectStorage.put(thumbPath, new ByteArrayInputStream(thumbBytes), thumbBytes.length, "image/webp");
                } catch (IOException e) {
                    // 缩略图失败不阻断主流程
                }
            });
        } catch (IOException e) {
            return DpAvatarUploadResult.fail("保存文件失败");
        } finally {
            if (tempOriginal != null) {
                try {
                    Files.deleteIfExists(tempOriginal);
                } catch (IOException ignored) {
                    // cleanup best-effort
                }
            }
        }

        LocalDateTime avatarUpdatedAt = LocalDateTime.now();
        if (dpUserMapper.updateAvatarUrl(stored.getId(), webPath, avatarUpdatedAt) != 1) {
            avatarStorageSupport.deleteWebPathFile(webPath);
            return DpAvatarUploadResult.fail("更新资料失败");
        }

        return DpAvatarUploadResult.ok(webPath, DpDateTimeSupport.toEpochMilli(avatarUpdatedAt));
    }
}
