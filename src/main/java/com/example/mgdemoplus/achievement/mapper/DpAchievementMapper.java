package com.example.mgdemoplus.achievement.mapper;

import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.achievement.vo.DpAchievementWallItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpAchievementMapper {

    @Select("SELECT * FROM dp_achievement WHERE code = #{code} LIMIT 1")
    DpAchievement selectByCode(@Param("code") String code);

    @Select("SELECT a.id, a.code, a.title, a.description, a.sort_order AS sortOrder, "
            + "COALESCE(ua.unlocked, 0) AS unlocked, ua.unlocked_at AS unlockedAt, "
            + "CASE WHEN COALESCE(ua.unlocked, 0) = 1 THEN ua.hand_history_id ELSE NULL END AS handHistoryId "
            + "FROM dp_achievement a "
            + "LEFT JOIN dp_user_achievement ua ON a.id = ua.achievement_id AND ua.user_id = #{userId} "
            + "ORDER BY a.sort_order ASC, a.id ASC")
    List<DpAchievementWallItemVO> selectWallForUser(@Param("userId") int userId);
}
