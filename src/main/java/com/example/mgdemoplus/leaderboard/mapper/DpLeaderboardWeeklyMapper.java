package com.example.mgdemoplus.leaderboard.mapper;

import com.example.mgdemoplus.leaderboard.entity.DpLeaderboardWeekly;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpLeaderboardWeeklyMapper {

    @Insert("INSERT INTO dp_leaderboard_weekly (week_monday, user_id, best_hand_multiplier, best_room_multiplier) "
            + "VALUES (#{weekMonday}, #{userId}, #{handMultiplier}, 0) "
            + "ON DUPLICATE KEY UPDATE "
            + "best_hand_multiplier = GREATEST(best_hand_multiplier, VALUES(best_hand_multiplier))")
    int upsertBestHand(@Param("weekMonday") LocalDate weekMonday,
                       @Param("userId") int userId,
                       @Param("handMultiplier") BigDecimal handMultiplier);

    @Insert("INSERT INTO dp_leaderboard_weekly (week_monday, user_id, best_hand_multiplier, best_room_multiplier) "
            + "VALUES (#{weekMonday}, #{userId}, 0, #{roomMultiplier}) "
            + "ON DUPLICATE KEY UPDATE "
            + "best_room_multiplier = GREATEST(best_room_multiplier, VALUES(best_room_multiplier))")
    int upsertBestRoom(@Param("weekMonday") LocalDate weekMonday,
                       @Param("userId") int userId,
                       @Param("roomMultiplier") BigDecimal roomMultiplier);

    @Select("SELECT week_monday AS weekMonday, user_id AS userId, "
            + "best_hand_multiplier AS bestHandMultiplier, best_room_multiplier AS bestRoomMultiplier, "
            + "updated_at AS updatedAt "
            + "FROM dp_leaderboard_weekly WHERE week_monday = #{weekMonday}")
    List<DpLeaderboardWeekly> selectAllForWeek(@Param("weekMonday") LocalDate weekMonday);

    @Select("SELECT week_monday AS weekMonday, user_id AS userId, "
            + "best_hand_multiplier AS bestHandMultiplier, best_room_multiplier AS bestRoomMultiplier, "
            + "updated_at AS updatedAt "
            + "FROM dp_leaderboard_weekly WHERE week_monday = #{weekMonday} AND user_id = #{userId}")
    DpLeaderboardWeekly selectByWeekAndUser(@Param("weekMonday") LocalDate weekMonday,
                                            @Param("userId") int userId);
}
