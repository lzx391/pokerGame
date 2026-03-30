package com.example.mgdemoplus.mapper.dp;

import com.example.mgdemoplus.dto.DpHandHistoryListItemDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 历史对局列表：dp_observed_hand_participant JOIN dp_observed_hand_history。
 */
@Mapper
public interface DpHandHistoryQueryMapper {

    /**
     * 已传 userId 且与昵称校验通过：按 user_id 命中，或仅按昵称快照且 user_id 为空的历史行。
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
            WHERE (p.user_id = #{userId}
               OR (p.nickname_snapshot = #{nickname} AND p.user_id IS NULL))
            ORDER BY h.ended_at_ms DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<DpHandHistoryListItemDTO> listForUserWithId(
            @Param("userId") int userId,
            @Param("nickname") String nickname,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    @Select("""
            SELECT COUNT(*)
            FROM dp_observed_hand_participant p
            INNER JOIN dp_observed_hand_history h ON h.id = p.hand_history_id
            WHERE (p.user_id = #{userId}
               OR (p.nickname_snapshot = #{nickname} AND p.user_id IS NULL))
            """)
    long countForUserWithId(@Param("userId") int userId, @Param("nickname") String nickname);

    /**
     * 未传 userId：仅按昵称快照（与 dp_user 昵称一致即可）。
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
            WHERE p.nickname_snapshot = #{nickname}
            ORDER BY h.ended_at_ms DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<DpHandHistoryListItemDTO> listForNicknameOnly(
            @Param("nickname") String nickname,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    @Select("""
            SELECT COUNT(*)
            FROM dp_observed_hand_participant p
            INNER JOIN dp_observed_hand_history h ON h.id = p.hand_history_id
            WHERE p.nickname_snapshot = #{nickname}
            """)
    long countForNicknameOnly(@Param("nickname") String nickname);
}
