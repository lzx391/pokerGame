package com.example.mgdemoplus.social.notify;

import com.example.mgdemoplus.social.impl.DpFriendSocialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 写操作成功后统一推送大厅社交通知 SSE。
 */
@Service
public class SocialNotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(SocialNotifyPublisher.class);

    private final SocialEventPublisher socialEventPublisher;
    private final DpFriendSocialService dpFriendSocialService;

    public SocialNotifyPublisher(
            SocialEventPublisher socialEventPublisher,
            @Lazy DpFriendSocialService dpFriendSocialService) {
        this.socialEventPublisher = socialEventPublisher;
        this.dpFriendSocialService = dpFriendSocialService;
    }

    public void notifyUser(int userId) {
        if (userId <= 0) {
            log.warn("[social-sse] notifyUser skipped invalid userId={}", userId);
            return;
        }
        log.info("[social-sse] notifyUser publish userId={}", userId);
        socialEventPublisher.publish(userId, "notify");
    }

    /**
     * 跟随进房等校正好友 presence 后，推送给观看方（通常为跟随者）以刷新好友列表 UI。
     */
    public void notifyFriendPresence(int watcherUserId, int friendUserId, String displayPresence, String reason) {
        if (watcherUserId <= 0 || friendUserId <= 0) {
            log.warn(
                    "[social-sse] notifyFriendPresence skipped watcherUserId={} friendUserId={}",
                    watcherUserId,
                    friendUserId);
            return;
        }
        log.info(
                "[social-sse] notifyFriendPresence publish watcherUserId={} friendUserId={} presence={}",
                watcherUserId,
                friendUserId,
                displayPresence);
        socialEventPublisher.publishPresence(watcherUserId, friendUserId, displayPresence, reason);
    }

    /** 由 SSE 心跳周期调用：过期进房邀请并通知被邀请人。 */
    public void expireDueRoomInvitesAndNotify() {
        dpFriendSocialService.expireDueRoomInvitesAndNotify();
    }
}
