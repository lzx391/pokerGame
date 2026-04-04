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
//已学习，本模块代码精讲如下：
//1. listForUserWithId方法：将userId和nickname传入，返回List<DpHandHistoryListItemDTO>对象
//2. listForNicknameOnly方法：将nickname传入，返回List<DpHandHistoryListItemDTO>对象
//3. countForUserWithId方法：将userId和nickname传入，返回long对象
//4. countForNicknameOnly方法：将nickname传入，返回long对象
//5. countParticipantForHandWithUserId方法：将handHistoryId和userId和nickname传入，返回long对象
//6. countParticipantForHandNicknameOnly方法：将handHistoryId和nickname传入，返回long对象

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

    /**
     * 当前用户是否可查看该手牌谱（与列表接口同一套 userId / 昵称规则）。
     */
    @Select("""
            SELECT COUNT(*)
            FROM dp_observed_hand_participant p
            WHERE p.hand_history_id = #{handHistoryId}
              AND (p.user_id = #{userId}
                   OR (p.nickname_snapshot = #{nickname} AND p.user_id IS NULL))
            """)
    long countParticipantForHandWithUserId(
            @Param("handHistoryId") long handHistoryId,
            @Param("userId") int userId,
            @Param("nickname") String nickname
    );

    @Select("""
            SELECT COUNT(*)
            FROM dp_observed_hand_participant p
            WHERE p.hand_history_id = #{handHistoryId}
              AND p.nickname_snapshot = #{nickname}
            """)
    long countParticipantForHandNicknameOnly(
            @Param("handHistoryId") long handHistoryId,
            @Param("nickname") String nickname
    );

    /**
     * 双方仅按昵称快照：同一 hand 上同时存在两人的参与者行。
     */
    @Select("""
            SELECT COUNT(DISTINCT h.id)
            FROM dp_observed_hand_history h
            INNER JOIN dp_observed_hand_participant p ON p.hand_history_id = h.id
                AND p.nickname_snapshot = #{nickname}
            INNER JOIN dp_observed_hand_participant o ON o.hand_history_id = h.id
                AND o.nickname_snapshot = #{otherNickname}
            """)
    long countCommonHandsNicknameOnly(
            @Param("nickname") String nickname,
            @Param("otherNickname") String otherNickname
    );

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
                AND p.nickname_snapshot = #{nickname}
            INNER JOIN dp_observed_hand_participant o ON o.hand_history_id = h.id
                AND o.nickname_snapshot = #{otherNickname}
            ORDER BY h.ended_at_ms DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<DpHandHistoryListItemDTO> listCommonHandsNicknameOnly(
            @Param("nickname") String nickname,
            @Param("otherNickname") String otherNickname,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /**
     * 当前用户按 userId/昵称规则；对方仅按昵称快照。
     */
    @Select("""
            SELECT COUNT(DISTINCT h.id)
            FROM dp_observed_hand_history h
            INNER JOIN dp_observed_hand_participant p ON p.hand_history_id = h.id
                AND (p.user_id = #{userId}
                     OR (p.nickname_snapshot = #{nickname} AND p.user_id IS NULL))
            INNER JOIN dp_observed_hand_participant o ON o.hand_history_id = h.id
                AND o.nickname_snapshot = #{otherNickname}
            """)
    long countCommonHandsCurrentUserIdOtherNickname(
            @Param("userId") int userId,
            @Param("nickname") String nickname,
            @Param("otherNickname") String otherNickname
    );

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
                AND (p.user_id = #{userId}
                     OR (p.nickname_snapshot = #{nickname} AND p.user_id IS NULL))
            INNER JOIN dp_observed_hand_participant o ON o.hand_history_id = h.id
                AND o.nickname_snapshot = #{otherNickname}
            ORDER BY h.ended_at_ms DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<DpHandHistoryListItemDTO> listCommonHandsCurrentUserIdOtherNickname(
            @Param("userId") int userId,
            @Param("nickname") String nickname,
            @Param("otherNickname") String otherNickname,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /**
     * 双方均按 userId/昵称规则（与列表权限一致）。
     */
    @Select("""
            SELECT COUNT(DISTINCT h.id)
            FROM dp_observed_hand_history h
            INNER JOIN dp_observed_hand_participant p ON p.hand_history_id = h.id
                AND (p.user_id = #{userId}
                     OR (p.nickname_snapshot = #{nickname} AND p.user_id IS NULL))
            INNER JOIN dp_observed_hand_participant o ON o.hand_history_id = h.id
                AND (o.user_id = #{otherUserId}
                     OR (o.nickname_snapshot = #{otherNickname} AND o.user_id IS NULL))
            """)
    long countCommonHandsBothUserIds(
            @Param("userId") int userId,
            @Param("nickname") String nickname,
            @Param("otherUserId") int otherUserId,
            @Param("otherNickname") String otherNickname
    );

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
                AND (p.user_id = #{userId}
                     OR (p.nickname_snapshot = #{nickname} AND p.user_id IS NULL))
            INNER JOIN dp_observed_hand_participant o ON o.hand_history_id = h.id
                AND (o.user_id = #{otherUserId}
                     OR (o.nickname_snapshot = #{otherNickname} AND o.user_id IS NULL))
            ORDER BY h.ended_at_ms DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<DpHandHistoryListItemDTO> listCommonHandsBothUserIds(
            @Param("userId") int userId,
            @Param("nickname") String nickname,
            @Param("otherUserId") int otherUserId,
            @Param("otherNickname") String otherNickname,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );
}
