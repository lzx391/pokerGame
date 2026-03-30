package com.example.mgdemoplus.mapper.dp;

import com.example.mgdemoplus.entity.dp.DpObservedHandHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

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
}
