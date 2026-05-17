package com.example.mgdemoplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.entity.DpFriendChatReadRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DpFriendChatReadMapper extends BaseMapper<DpFriendChatReadRow> {

    @Insert("""
            INSERT INTO dp_friend_chat_read (user_id, peer_user_id, last_read_message_id, updated_at)
            VALUES (#{userId}, #{peerUserId}, #{lastReadMessageId}, NOW(3))
            ON DUPLICATE KEY UPDATE
                last_read_message_id = GREATEST(last_read_message_id, VALUES(last_read_message_id)),
                updated_at = NOW(3)
            """)
    int upsertLastRead(
            @Param("userId") int userId,
            @Param("peerUserId") int peerUserId,
            @Param("lastReadMessageId") long lastReadMessageId);
}
