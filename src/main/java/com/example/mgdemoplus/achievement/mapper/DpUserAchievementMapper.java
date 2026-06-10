package com.example.mgdemoplus.achievement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpUserAchievementMapper {

    @Select("SELECT unlocked FROM dp_user_achievement "
            + "WHERE user_id = #{userId} AND achievement_id = #{achievementId} LIMIT 1")
    Integer selectUnlockedFlag(@Param("userId") int userId, @Param("achievementId") int achievementId);

    /**
     * 首次解锁：已解锁(1)时不重复写 unlocked_at。
     */
    @Insert("INSERT INTO dp_user_achievement (user_id, achievement_id, unlocked, unlocked_at, hand_history_id) "
            + "VALUES (#{userId}, #{achievementId}, 1, NOW(), #{handHistoryId}) "
            + "ON DUPLICATE KEY UPDATE "
            + "unlocked = IF(dp_user_achievement.unlocked = 0, 1, dp_user_achievement.unlocked), "
            + "unlocked_at = IF(dp_user_achievement.unlocked = 0, NOW(), dp_user_achievement.unlocked_at), "
            + "hand_history_id = IF(dp_user_achievement.unlocked = 0 AND #{handHistoryId} IS NOT NULL, "
            + "#{handHistoryId}, dp_user_achievement.hand_history_id)")
    int tryUnlock(@Param("userId") int userId,
                  @Param("achievementId") int achievementId,
                  @Param("handHistoryId") Long handHistoryId);
}
