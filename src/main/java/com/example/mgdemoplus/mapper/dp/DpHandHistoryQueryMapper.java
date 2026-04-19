package com.example.mgdemoplus.mapper.dp;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.example.mgdemoplus.vo.DpHandHistoryListItemVO;

import java.util.List;

/**
 * 历史对局列表：dp_observed_hand_participant JOIN dp_observed_hand_history。
 */
@Mapper
public interface DpHandHistoryQueryMapper {
// 列表查询不含 LIMIT，由 PageHelper 分页；总条数由 PageInfo 取自拦截器 count。

    /**
     * 按参与者表 user_id 列出该账号的历史对局（仅真人账号有行时可见）。
     */
    @Select("""
            SELECT h.id AS handHistoryId, h.room_id AS roomId, h.hand_seed AS handSeed,
                   h.started_at_ms AS startedAtMs, h.ended_at_ms AS endedAtMs,
                   h.small_blind_chips AS smallBlindChips, h.big_blind_chips AS bigBlindChips,
                   h.dealer_nickname AS dealerNickname,
                   h.main_pot_before_settlement AS mainPotBeforeSettlement,
                   p.seat_index AS seatIndex, p.is_dealer AS dealer, p.blind_pos AS blindPos,
                   p.net_chips AS netChips
            FROM dp_observed_hand_participant p
            INNER JOIN dp_observed_hand_history h ON h.id = p.hand_history_id
            WHERE p.user_id = #{userId}
            ORDER BY h.ended_at_ms DESC
            """)
    List<DpHandHistoryListItemVO> listForUserId(@Param("userId") int userId);

    @Select("""
            SELECT COUNT(*)
            FROM dp_observed_hand_participant p
            WHERE p.hand_history_id = #{handHistoryId}
              AND p.user_id = #{userId}
            """)
    long countParticipantForHand(
            @Param("handHistoryId") long handHistoryId,
            @Param("userId") int userId
    );

    /**
     * 双方均以 user_id 参与同一手牌谱时的共同对局列表。
     */
    @Select("""
            SELECT h.id AS handHistoryId, h.room_id AS roomId, h.hand_seed AS handSeed,
                   h.started_at_ms AS startedAtMs, h.ended_at_ms AS endedAtMs,
                   h.small_blind_chips AS smallBlindChips, h.big_blind_chips AS bigBlindChips,
                   h.dealer_nickname AS dealerNickname,
                   h.main_pot_before_settlement AS mainPotBeforeSettlement,
                   p.seat_index AS seatIndex, p.is_dealer AS dealer, p.blind_pos AS blindPos,
                   p.net_chips AS netChips
            FROM dp_observed_hand_history h
            INNER JOIN dp_observed_hand_participant p ON p.hand_history_id = h.id
                AND p.user_id = #{userId}
            INNER JOIN dp_observed_hand_participant o ON o.hand_history_id = h.id
                AND o.user_id = #{otherUserId}
            ORDER BY h.ended_at_ms DESC
            """)
    List<DpHandHistoryListItemVO> listCommonHandsBothUserIds(
            @Param("userId") int userId,
            @Param("otherUserId") int otherUserId
    );
}
