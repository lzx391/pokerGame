package com.example.mgdemoplus.mapper.dp;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.example.mgdemoplus.entity.dp.DpRoom;

import java.util.List;

@Mapper
public interface DpRoomLobbyMapper {

    @Insert("""
            INSERT INTO dp_room_lobby
            (room_id, owner_nickname, room_status, player_count, small_blind_chips, big_blind_chips, starting_stack_bb, password_protected, max_seat_count)
            VALUES
            (#{roomId}, #{owner}, 0, #{playerSize}, #{smallBlindChips}, #{bigBlindChips}, #{startingStackBb}, #{passwordProtected}, #{maxSeatCount})
            """)
    int insertRoomSummary(DpRoom row);

    @Update("""
            UPDATE dp_room_lobby
            SET owner_nickname = #{owner},
                room_status = 0,
                player_count = #{playerSize},
                small_blind_chips = #{smallBlindChips},
                big_blind_chips = #{bigBlindChips},
                starting_stack_bb = #{startingStackBb},
                password_protected = #{passwordProtected},
                max_seat_count = #{maxSeatCount}
            WHERE room_id = #{roomId}
            """)
    int updateRoomSummary(DpRoom row);

    @Update("""
            UPDATE dp_room_lobby
            SET room_status = 1
            WHERE room_id = #{roomId}
            """)
    int deleteRoomSummaryByRoomId(String roomId);

    @Select("""
            SELECT room_id AS roomId,
                   owner_nickname AS owner,
                   player_count AS playerSize,
                   small_blind_chips AS smallBlindChips,
                   big_blind_chips AS bigBlindChips,
                   starting_stack_bb AS startingStackBb,
                   password_protected AS passwordProtected,
                   max_seat_count AS maxSeatCount
            FROM dp_room_lobby
            WHERE room_status = 0
            ORDER BY created_at DESC
            """)
    List<DpRoom> selectActiveRoomSummaries();

    /** 当前仍展示在大厅的房间号（room_status = 0） */
    @Select("SELECT room_id FROM dp_room_lobby WHERE room_status = 0")
    List<String> selectActiveLobbyRoomIds();
}
