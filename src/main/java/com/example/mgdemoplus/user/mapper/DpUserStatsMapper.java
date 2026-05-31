package com.example.mgdemoplus.user.mapper;

import com.example.mgdemoplus.common.entity.DpUserStats;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DpUserStatsMapper {

    @Select("SELECT * FROM dp_user_stats WHERE user_id = #{userId}")
    DpUserStats selectByUserId(int userId);

    /**
     * 每手结算后一次性写入：局数 +1，牌型荣誉按传入增量累加，单局净赢取 GREATER。
     * 零增量调用即纯计局数（零底池等场景）。
     */
    @Insert("INSERT INTO dp_user_stats (user_id, royal_flush_wins, straight_flush_wins, four_of_a_kind_wins, largest_pot_won, total_hands_played) "
            + "VALUES (#{userId}, #{royalFlushInc}, #{straightFlushInc}, #{fourOfAKindInc}, #{largestPotWon}, 1) "
            + "ON DUPLICATE KEY UPDATE "
            + "royal_flush_wins = royal_flush_wins + VALUES(royal_flush_wins), "
            + "straight_flush_wins = straight_flush_wins + VALUES(straight_flush_wins), "
            + "four_of_a_kind_wins = four_of_a_kind_wins + VALUES(four_of_a_kind_wins), "
            + "largest_pot_won = GREATEST(largest_pot_won, VALUES(largest_pot_won)), "
            + "total_hands_played = total_hands_played + 1")
    int upsertAfterHand(@Param("userId") int userId,
                        @Param("royalFlushInc") int royalFlushInc,
                        @Param("straightFlushInc") int straightFlushInc,
                        @Param("fourOfAKindInc") int fourOfAKindInc,
                        @Param("largestPotWon") BigDecimal largestPotWon);

    /**
     * 更新单房间最高净赢倍数：只有新值更大时才覆盖（离座/退房结算时调用）。
     */
    @Update("UPDATE dp_user_stats SET largest_room_net = #{multiplier} "
            + "WHERE user_id = #{userId} AND largest_room_net < #{multiplier}")
    int tryUpdateLargestRoomNet(@Param("userId") int userId, @Param("multiplier") BigDecimal multiplier);

    /** 周榜前三结算：无 stats 行则 insert，有则累加。 */
    @Insert("INSERT INTO dp_user_stats (user_id, leaderboard_top_count) VALUES (#{userId}, #{inc}) "
            + "ON DUPLICATE KEY UPDATE leaderboard_top_count = leaderboard_top_count + VALUES(leaderboard_top_count)")
    int incrementLeaderboardTopCount(@Param("userId") int userId, @Param("inc") int inc);
}
