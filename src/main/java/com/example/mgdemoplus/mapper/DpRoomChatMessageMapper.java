package com.example.mgdemoplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.entity.DpRoomChatMessageRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DpRoomChatMessageMapper extends BaseMapper<DpRoomChatMessageRow> {

    @Insert("""
            <script>
            INSERT INTO dp_room_chat_message
                (id, room_id, sender_user_id, sender_nickname, body, server_time_ms, created_at)
            VALUES
            <foreach collection="rows" item="r" separator=",">
                (#{r.id}, #{r.roomId}, #{r.senderUserId}, #{r.senderNickname}, #{r.body},
                 #{r.serverTimeMs}, NOW(3))
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<DpRoomChatMessageRow> rows);
}
