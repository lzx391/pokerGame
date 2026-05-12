package com.example.mgdemoplus.mapper.dp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.entity.dp.DpRoomInviteRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpRoomInviteMapper extends BaseMapper<DpRoomInviteRow> {

    @Select("""
            SELECT i.id               AS id,
                   i.inviter_user_id  AS inviterUserId,
                   i.invitee_user_id  AS inviteeUserId,
                   i.room_id          AS roomId,
                   i.status           AS status,
                   i.created_at       AS createdAt,
                   i.expires_at       AS expiresAt,
                   u.nickname         AS inviterNickname
            FROM dp_room_invite i
                     INNER JOIN dp_user u ON u.id = i.inviter_user_id
            WHERE i.invitee_user_id = #{inviteeUserId}
              AND i.status = 'PENDING'
              AND i.expires_at > NOW(3)
            ORDER BY i.created_at DESC
            """)
    List<DpRoomInviteRow> listPendingRoomInvitesToUser(@Param("inviteeUserId") int inviteeUserId);
}
