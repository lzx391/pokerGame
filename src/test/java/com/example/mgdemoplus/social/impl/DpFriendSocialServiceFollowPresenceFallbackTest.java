package com.example.mgdemoplus.social.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.presence.DpSitePresenceService;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.social.mapper.DpFriendLinkMapper;
import com.example.mgdemoplus.social.mapper.DpFriendRequestMapper;
import com.example.mgdemoplus.roomchat.mapper.DpRoomInviteMapper;
import com.example.mgdemoplus.social.notify.SocialNotifyPublisher;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跟随进房失败：校正好友 presence 并向跟随者推 SSE。
 */
@ExtendWith(MockitoExtension.class)
class DpFriendSocialServiceFollowPresenceFallbackTest {

    @Mock
    private DpFriendRequestMapper friendRequestMapper;
    @Mock
    private DpFriendLinkMapper friendLinkMapper;
    @Mock
    private DpRoomInviteMapper roomInviteMapper;
    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpRoomService dpRoomService;
    @Mock
    private DpFriendPresenceService friendPresenceService;
    @Mock
    private DpSitePresenceService sitePresenceService;
    @Mock
    private SocialNotifyPublisher socialNotifyPublisher;

    @InjectMocks
    private DpFriendSocialService service;

    @Test
    void followFriendToTheirRoom_notInRoom_marksIdleAndPushesPresenceToFollower() {
        when(friendLinkMapper.selectCount(any())).thenReturn(1L);

        DpUser friend = new DpUser();
        friend.setId(2);
        friend.setNickname("Bob");
        when(dpUserMapper.selectById(2)).thenReturn(friend);
        when(dpRoomService.findRoomIdContainingNickname("Bob")).thenReturn(null);
        when(sitePresenceService.isOnlineSite(2)).thenReturn(true);

        ResultUtil res = service.followFriendToTheirRoom(1, "Alice", 2);

        assertThat(Boolean.TRUE.equals(res.getSuccess())).isFalse();
        assertThat(res.getData().get("message")).isEqualTo("好友不在房间内");
        verify(friendPresenceService).markIdle(2, "followFriendToTheirRoom:not_in_room");
        verify(socialNotifyPublisher)
                .notifyFriendPresence(1, 2, "IDLE", "followFriendToTheirRoom:not_in_room");
    }

    @Test
    void followFriendToTheirRoom_roomGone_marksIdleAndPushesPresenceToFollower() {
        when(friendLinkMapper.selectCount(any())).thenReturn(1L);

        DpUser friend = new DpUser();
        friend.setId(2);
        friend.setNickname("Bob");
        when(dpUserMapper.selectById(2)).thenReturn(friend);
        when(dpRoomService.findRoomIdContainingNickname("Bob")).thenReturn("room-x");
        when(dpRoomService.dpRoomExistsInMemory("room-x")).thenReturn(false);
        when(sitePresenceService.isOnlineSite(2)).thenReturn(false);

        ResultUtil res = service.followFriendToTheirRoom(1, "Alice", 2);

        assertThat(Boolean.TRUE.equals(res.getSuccess())).isFalse();
        verify(friendPresenceService).markIdle(2, "followFriendToTheirRoom:room_gone");
        verify(socialNotifyPublisher)
                .notifyFriendPresence(eq(1), eq(2), eq("OFFLINE"), eq("followFriendToTheirRoom:room_gone"));
    }
}
