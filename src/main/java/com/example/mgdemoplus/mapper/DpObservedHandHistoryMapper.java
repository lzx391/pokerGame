package com.example.mgdemoplus.mapper;

import com.example.mgdemoplus.entity.DpObservedHandHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

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
    int insert(DpObservedHandHistory row);
}
