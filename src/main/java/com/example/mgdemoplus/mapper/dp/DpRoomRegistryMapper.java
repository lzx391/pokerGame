package com.example.mgdemoplus.mapper.dp;

import com.example.mgdemoplus.entity.dp.DpRoomRegistry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DpRoomRegistryMapper {

    @Insert("INSERT INTO dp_room_registry (room_id, game_code, shard_id, ws_route, status, has_password, max_seats, player_count, spectator_count, owner_nickname, "
            + "small_blind_chips, big_blind_chips, starting_stack_bb) "
            + "VALUES (#{roomId}, #{gameCode}, #{shardId}, #{wsRoute}, #{status}, #{hasPassword}, #{maxSeats}, #{playerCount}, #{spectatorCount}, #{ownerNickname}, "
            + "#{smallBlindChips}, #{bigBlindChips}, #{startingStackBb})")
    int insert(DpRoomRegistry row);

    @Update("UPDATE dp_room_registry SET status = #{status}, has_password = #{hasPassword}, player_count = #{playerCount}, "
            + "spectator_count = #{spectatorCount}, owner_nickname = #{ownerNickname}, small_blind_chips = #{smallBlindChips}, big_blind_chips = #{bigBlindChips}, "
            + "starting_stack_bb = #{startingStackBb}, updated_at = CURRENT_TIMESTAMP(3) WHERE room_id = #{roomId} AND closed_at IS NULL")
    int updateSnapshot(DpRoomRegistry row);

    @Update("UPDATE dp_room_registry SET status = 2, closed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3) WHERE room_id = #{roomId} AND closed_at IS NULL")
    int closeByRoomId(String roomId);

    @Select("SELECT id, room_id, game_code, shard_id, ws_route, status, has_password, max_seats, player_count, spectator_count, owner_nickname, "
            + "small_blind_chips, big_blind_chips, starting_stack_bb, created_at, updated_at, closed_at FROM dp_room_registry WHERE room_id = #{roomId}")
    DpRoomRegistry selectByRoomId(String roomId);

    @Select("SELECT id, room_id, game_code, shard_id, ws_route, status, has_password, max_seats, player_count, spectator_count, owner_nickname, "
            + "small_blind_chips, big_blind_chips, starting_stack_bb, created_at, updated_at, closed_at FROM dp_room_registry WHERE status IN (0, 1) AND closed_at IS NULL ORDER BY updated_at DESC LIMIT 200")
    List<DpRoomRegistry> selectActiveList();
}
