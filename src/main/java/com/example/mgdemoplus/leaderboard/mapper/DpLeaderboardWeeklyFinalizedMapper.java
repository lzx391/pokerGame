package com.example.mgdemoplus.leaderboard.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpLeaderboardWeeklyFinalizedMapper {

    @Select("SELECT MAX(week_monday) FROM dp_leaderboard_weekly_finalized")
    LocalDate selectMaxFinalizedWeek();

    @Select("SELECT COUNT(*) FROM dp_leaderboard_weekly_finalized WHERE week_monday = #{weekMonday}")
    int countByWeek(@Param("weekMonday") LocalDate weekMonday);

    /** 插入成功返回 1，已存在返回 0（幂等）。 */
    @Insert("INSERT IGNORE INTO dp_leaderboard_weekly_finalized (week_monday) VALUES (#{weekMonday})")
    int tryMarkFinalized(@Param("weekMonday") LocalDate weekMonday);
}
