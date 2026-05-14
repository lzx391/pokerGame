package com.example.mgdemoplus.mapper;

import com.example.mgdemoplus.entity.DpObservedHandHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpObservedHandHistoryMapper {

    @Insert("""
            INSERT INTO dp_observed_hand_history (
                room_id, hand_seed, started_at_ms, ended_at_ms,
                small_blind_chips, big_blind_chips, dealer_nickname,
                main_pot_before_settlement, payload_version, payload_json
            ) VALUES (
                #{roomId}, #{handSeed}, #{startedAtMs}, #{endedAtMs},
                #{smallBlindChips}, #{bigBlindChips}, #{dealerNickname},
                #{mainPotBeforeSettlement}, #{payloadVersion}, #{payloadJson}
            )
            """)
    //这里是为了获取插入后的id给dp_observed_hand_participant表使用
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DpObservedHandHistory row);

    @Select("""
            SELECT id, room_id AS roomId, hand_seed AS handSeed,
                   started_at_ms AS startedAtMs, ended_at_ms AS endedAtMs,
                   small_blind_chips AS smallBlindChips, big_blind_chips AS bigBlindChips,
                   dealer_nickname AS dealerNickname,
                   main_pot_before_settlement AS mainPotBeforeSettlement,
                   payload_version AS payloadVersion, payload_json AS payloadJson
            FROM dp_observed_hand_history WHERE id = #{id}
            """)
    DpObservedHandHistory selectById(long id);
}
