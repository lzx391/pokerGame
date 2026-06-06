package com.example.mgdemoplus.achievement;

import com.example.mgdemoplus.achievement.vo.DpAchievementWallItemVO;

import java.util.List;

public interface DpAchievementService {

    String CODE_QUAD_NIGHTMARE = "quad_nightmare";

    List<DpAchievementWallItemVO> buildWallForUser(int userId);

    void unlockIfAbsent(int userId, String achievementCode);
}
