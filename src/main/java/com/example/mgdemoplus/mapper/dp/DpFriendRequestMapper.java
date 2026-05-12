package com.example.mgdemoplus.mapper.dp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.entity.dp.DpFriendRequestRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpFriendRequestMapper extends BaseMapper<DpFriendRequestRow> {

    @Select("""
            SELECT r.id            AS id,
                   r.from_user_id  AS fromUserId,
                   r.to_user_id    AS toUserId,
                   r.status        AS status,
                   r.created_at    AS createdAt,
                   uf.nickname     AS fromNickname
            FROM dp_friend_request r
                     INNER JOIN dp_user uf ON uf.id = r.from_user_id
            WHERE r.to_user_id = #{toUserId}
              AND r.status = 'PENDING'
            ORDER BY r.created_at DESC
            """)
    List<DpFriendRequestRow> listPendingFriendRequestsToUser(@Param("toUserId") int toUserId);
}
