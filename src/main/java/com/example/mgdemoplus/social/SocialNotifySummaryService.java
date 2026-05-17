package com.example.mgdemoplus.social;

import com.example.mgdemoplus.service.serviceImpl.DpFriendChatService;
import com.example.mgdemoplus.service.serviceImpl.DpFriendSocialService;
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
        int mailboxUnread = dpFriendSocialService.mailboxUnreadCount(userId);
        var byFriend = dpFriendChatService.friendChatUnreadByFriendUserId(userId);
        long total = byFriend.values().stream().mapToLong(Integer::longValue).sum();
        return new SocialNotifyPayload(mailboxUnread, total, byFriend);
    }
}
