package com.example.mgdemoplus.social.notify;

import com.example.mgdemoplus.social.impl.DpFriendChatService;
import com.example.mgdemoplus.social.impl.DpFriendSocialService;
import org.springframework.stereotype.Service;

/**
 * 聚合邮箱未读 + 好友私信未读，供 SSE 与 REST 摘要共用。
 */
@Service
public class SocialNotifySummaryService {

    private final DpFriendSocialService dpFriendSocialService;
    private final DpFriendChatService dpFriendChatService;

    public SocialNotifySummaryService(
            DpFriendSocialService dpFriendSocialService, DpFriendChatService dpFriendChatService) {
        this.dpFriendSocialService = dpFriendSocialService;
        this.dpFriendChatService = dpFriendChatService;
    }

    public SocialNotifyPayload buildForUser(int userId) {
        //算好友申请和邀请未读总数
        int mailboxUnread = dpFriendSocialService.mailboxUnreadCount(userId);
        //构造好友和未读消息数MAP,Map<Integer, Integer>是var
        var byFriend = dpFriendChatService.friendChatUnreadByFriendUserId(userId);
        //把好友消息未读总数算出来
        long total = byFriend.values().stream().mapToLong(Integer::longValue).sum();
        return new SocialNotifyPayload(mailboxUnread, total, byFriend);
    }
}
