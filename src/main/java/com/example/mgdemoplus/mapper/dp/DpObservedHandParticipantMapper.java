package com.example.mgdemoplus.mapper.dp;

import com.example.mgdemoplus.entity.dp.DpObservedHandParticipant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DpObservedHandParticipantMapper {

    @Insert("""
            INSERT INTO dp_observed_hand_participant (
                hand_history_id, user_id, nickname_snapshot,
                seat_index, is_dealer, blind_pos, net_chips
            ) VALUES (
                #{handHistoryId}, #{userId}, #{nicknameSnapshot},
                #{seatIndex}, #{dealer}, #{blindPos}, #{netChips}
            )
            """)
    int insert(DpObservedHandParticipant row);
}
