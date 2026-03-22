package com.example.mgdemoplus.mapper.dp;

import com.example.mgdemoplus.entity.dp.DpSharkOpponentProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpSharkOpponentProfileMapper {

    @Select("""
            SELECT player_nickname, stats_json, learned_json, payload_version
            FROM dp_shark_opponent_profile
            WHERE player_nickname = #{nickname}
            """)
    DpSharkOpponentProfile selectByNickname(String nickname);

    @Insert("""
            INSERT INTO dp_shark_opponent_profile (
                player_nickname, stats_json, learned_json, payload_version
            ) VALUES (
                #{playerNickname}, #{statsJson}, #{learnedJson}, #{payloadVersion}
            )
            ON DUPLICATE KEY UPDATE
                stats_json = VALUES(stats_json),
                learned_json = VALUES(learned_json),
                payload_version = VALUES(payload_version),
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(DpSharkOpponentProfile row);
}
