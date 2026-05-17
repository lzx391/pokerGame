package com.example.mgdemoplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.entity.DpFriendMessageRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpFriendMessageMapper extends BaseMapper<DpFriendMessageRow> {

    @Select("""
            <script>
            SELECT id, sender_user_id AS senderUserId, recipient_user_id AS recipientUserId,
                   body, created_at AS createdAt
            FROM dp_friend_message
            WHERE ((sender_user_id = #{userA} AND recipient_user_id = #{userB})
                OR (sender_user_id = #{userB} AND recipient_user_id = #{userA}))
            <if test="beforeId != null">AND id &lt; #{beforeId}</if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<DpFriendMessageRow> listConversation(
            @Param("userA") int userA,
            @Param("userB") int userB,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM dp_friend_message
            WHERE (sender_user_id = #{userA} AND recipient_user_id = #{userB})
               OR (sender_user_id = #{userB} AND recipient_user_id = #{userA})
            """)
    long countConversation(@Param("userA") int userA, @Param("userB") int userB);

    @Delete("""
            DELETE FROM dp_friend_message
            WHERE id IN (
                SELECT id FROM (
                    SELECT id FROM dp_friend_message
                    WHERE (sender_user_id = #{userA} AND recipient_user_id = #{userB})
                       OR (sender_user_id = #{userB} AND recipient_user_id = #{userA})
                    ORDER BY id ASC
                    LIMIT #{deleteCount}
                ) t
            )
            """)
    int deleteOldestInConversation(
            @Param("userA") int userA,
            @Param("userB") int userB,
            @Param("deleteCount") int deleteCount);

    @Select("""
            SELECT COUNT(*)
            FROM dp_friend_message m
            WHERE m.recipient_user_id = #{me}
              AND m.sender_user_id = #{peer}
              AND m.id > COALESCE(
                  (SELECT r.last_read_message_id
                   FROM dp_friend_chat_read r
                   WHERE r.user_id = #{me} AND r.peer_user_id = #{peer}),
                  0)
            """)
    long countUnreadFromPeer(@Param("me") int me, @Param("peer") int peer);
}
