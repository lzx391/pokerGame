package com.example.mgdemoplus.achievement;

import com.example.mgdemoplus.achievement.vo.DpAchievementWallItemVO;

import java.util.List;

public interface DpAchievementService {

    String CODE_QUAD_NIGHTMARE = "quad_nightmare";
    String CODE_TWENTY_SEVEN_TERMINATOR = "twenty_seven_terminator";
    String CODE_THRONE_USURPER = "throne_usurper";
    String CODE_TABLE_CLEAR = "table_clear";
    String CODE_DRAW_INSULATOR = "draw_insulator";
    String CODE_NATURAL_DISASTER = "natural_disaster";
    String CODE_SOUL_READER = "soul_reader";
    String CODE_SWEEP_ALL = "sweep_all";
    String CODE_ONE_STREET_HEAVEN = "one_street_heaven";
    String CODE_FINAL_ORACLE = "final_oracle";
    String CODE_MIRROR_DUEL = "mirror_duel";

    List<DpAchievementWallItemVO> buildWallForUser(int userId);

    void unlockIfAbsent(int userId, String achievementCode, Long handHistoryId);
}
