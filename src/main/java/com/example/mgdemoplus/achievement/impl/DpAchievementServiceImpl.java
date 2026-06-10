package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.achievement.mapper.DpAchievementMapper;
import com.example.mgdemoplus.achievement.mapper.DpUserAchievementMapper;
import com.example.mgdemoplus.achievement.notify.AchievementNotifyPublisher;
import com.example.mgdemoplus.achievement.vo.DpAchievementWallItemVO;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DpAchievementServiceImpl implements DpAchievementService {

    @Autowired
    private DpAchievementMapper dpAchievementMapper;
    @Autowired
    private DpUserAchievementMapper dpUserAchievementMapper;
    @Autowired
    private DpUserMapper dpUserMapper;
    @Autowired
    private AchievementNotifyPublisher achievementNotifyPublisher;

    @Override
    public List<DpAchievementWallItemVO> buildWallForUser(int userId) {
        if (dpUserMapper.selectById(userId) == null) {
            return null;
        }
        return dpAchievementMapper.selectWallForUser(userId);
    }

    @Override
    public void unlockIfAbsent(int userId, String achievementCode, Long handHistoryId) {
        if (userId <= 0 || achievementCode == null || achievementCode.isBlank()) {
            return;
        }
        DpAchievement def = dpAchievementMapper.selectByCode(achievementCode);
        if (def == null) {
            return;
        }
        Integer unlocked = dpUserAchievementMapper.selectUnlockedFlag(userId, def.getId());
        if (unlocked != null && unlocked == 1) {
            return;
        }
        dpUserAchievementMapper.tryUnlock(userId, def.getId(), handHistoryId);
        achievementNotifyPublisher.notifyUnlocked(userId, def);
    }
}
