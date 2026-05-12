package com.example.mgdemoplus.mapper.dp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.entity.dp.DpFriendLinkRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpFriendLinkMapper extends BaseMapper<DpFriendLinkRow> {

    @Select("""
            SELECT fl.id                                                                      AS id,
                   fl.user_low_id                                                             AS userLowId,
                   fl.user_high_id                                                            AS userHighId,
                   fl.created_at                                                                AS createdAt,
                   CASE WHEN fl.user_low_id = #{userId} THEN fl.user_high_id ELSE fl.user_low_id END AS friendUserId,
                   CASE WHEN fl.user_low_id = #{userId} THEN hu.nickname ELSE lu.nickname END         AS friendNickname
            FROM dp_friend_link fl
                     INNER JOIN dp_user lu ON lu.id = fl.user_low_id
                     INNER JOIN dp_user hu ON hu.id = fl.user_high_id
            WHERE fl.user_low_id = #{userId}
               OR fl.user_high_id = #{userId}
            ORDER BY fl.created_at DESC
            """)
    List<DpFriendLinkRow> listFriendsOfUser(@Param("userId") int userId);
}
