package com.example.mgdemoplus.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.social.entity.DpFriendLinkRow;
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
                   CASE WHEN fl.user_low_id = #{userId} THEN hu.nickname ELSE lu.nickname END         AS friendNickname,
                   CASE WHEN fl.user_low_id = #{userId} THEN hu.avatar_url ELSE lu.avatar_url END     AS friendAvatarUrl,
                   CASE WHEN fl.user_low_id = #{userId} THEN hu.avatar_updated_at ELSE lu.avatar_updated_at END AS friendAvatarUpdatedAt
            FROM dp_friend_link fl
                     INNER JOIN dp_user lu ON lu.id = fl.user_low_id
                     INNER JOIN dp_user hu ON hu.id = fl.user_high_id
            WHERE fl.user_low_id = #{userId}
               OR fl.user_high_id = #{userId}
            ORDER BY fl.created_at DESC
            """)
    List<DpFriendLinkRow> listFriendsOfUser(@Param("userId") int userId);

    /**
     * 好友列表：按与当前用户最近私信时间（无则好友建立时间）降序，再昵称升序；支持 id / 昵称筛选。
     * 与 {@link com.github.pagehelper.PageHelper} 联用分页。
     */
    @Select(
            """
            <script>
            SELECT fl.id                                                                      AS id,
                   fl.user_low_id                                                             AS userLowId,
                   fl.user_high_id                                                            AS userHighId,
                   fl.created_at                                                              AS createdAt,
                   CASE WHEN fl.user_low_id = #{userId} THEN fl.user_high_id ELSE fl.user_low_id END AS friendUserId,
                   CASE WHEN fl.user_low_id = #{userId} THEN hu.nickname ELSE lu.nickname END         AS friendNickname,
                   CASE WHEN fl.user_low_id = #{userId} THEN hu.avatar_url ELSE lu.avatar_url END     AS friendAvatarUrl,
                   CASE WHEN fl.user_low_id = #{userId} THEN hu.avatar_updated_at ELSE lu.avatar_updated_at END AS friendAvatarUpdatedAt
            FROM dp_friend_link fl
                     INNER JOIN dp_user lu ON lu.id = fl.user_low_id
                     INNER JOIN dp_user hu ON hu.id = fl.user_high_id
                     LEFT JOIN (
                SELECT CASE
                           WHEN m.sender_user_id = #{userId} THEN m.recipient_user_id
                           ELSE m.sender_user_id END AS peer_id,
                       MAX(m.created_at)               AS last_msg_at
                FROM dp_friend_message m
                WHERE m.sender_user_id = #{userId}
                   OR m.recipient_user_id = #{userId}
                GROUP BY peer_id
            ) msg ON msg.peer_id = (CASE WHEN fl.user_low_id = #{userId} THEN fl.user_high_id ELSE fl.user_low_id END)
            WHERE (fl.user_low_id = #{userId}
                OR fl.user_high_id = #{userId})
            <if test="filterFriendUserId != null">
              AND (CASE WHEN fl.user_low_id = #{userId} THEN fl.user_high_id ELSE fl.user_low_id END) = #{filterFriendUserId}
            </if>
            <if test="nicknameContains != null and nicknameContains != ''">
              AND LOWER(CASE WHEN fl.user_low_id = #{userId} THEN hu.nickname ELSE lu.nickname END)
                  LIKE CONCAT('%', LOWER(#{nicknameContains}), '%')
            </if>
            ORDER BY COALESCE(msg.last_msg_at, fl.created_at) DESC,
                     (CASE WHEN fl.user_low_id = #{userId} THEN hu.nickname ELSE lu.nickname END) ASC
            </script>
            """)
    List<DpFriendLinkRow> listFriendsOfUserPaged(
            @Param("userId") int userId,
            @Param("filterFriendUserId") Integer filterFriendUserId,
            @Param("nicknameContains") String nicknameContains);
}
