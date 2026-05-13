package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpFriendLinkMapper;
import com.example.mgdemoplus.mapper.dp.DpFriendRequestMapper;
import com.example.mgdemoplus.mapper.dp.DpRoomInviteMapper;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.example.mgdemoplus.service.dp.DpFriendPresenceService;
import com.example.mgdemoplus.service.dp.DpSitePresenceService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 跟随进房：与邮箱邀约共用 {@link DpRoomServiceImpl#joinRoomInviteAsSpectator}。
 */
@ExtendWith(MockitoExtension.class)
class DpFriendSocialServiceFollowSpectateTest {

    @Mock
    private DpFriendRequestMapper friendRequestMapper;
    @Mock
    private DpFriendLinkMapper friendLinkMapper;
    @Mock
    private DpRoomInviteMapper roomInviteMapper;
    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private DpRoomServiceImpl dpRoomService;
    @Mock
    private DpFriendPresenceService friendPresenceService;

    @Mock
    private DpSitePresenceService sitePresenceService;

    @InjectMocks
    private DpFriendSocialService service;

    @Test
    void followFriendIntoRoomAsSpectator_notFriends_sameMessageAsInvite() {
        when(friendLinkMapper.selectCount(any())).thenReturn(0L);

        ResultUtil res = service.followFriendIntoRoomAsSpectator(1, "me", 2);
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isFalse();
        assertThat(res.getData().get("message")).isEqualTo("仅互为好友可邀请");
    }

    @Test
    void followFriendIntoRoomAsSpectator_delegatesToJoinRoomInviteAsSpectator() {
        when(friendLinkMapper.selectCount(any())).thenReturn(1L);

        DpUser bob = new DpUser();
        bob.setId(2);
        bob.setNickname("Bob");
        when(dpUserMapper.selectById(2)).thenReturn(bob);

        when(dpRoomService.findActiveRoomIdForNickname("Bob")).thenReturn("room-x");
        when(dpRoomService.dpRoomExistsInMemory("room-x")).thenReturn(true);
        when(dpRoomService.canActiveMemberInviteFriends(eq("room-x"), eq("Bob"))).thenReturn(true);

        when(dpRoomService.joinRoomInviteAsSpectator("room-x", "me", 1)).thenReturn("ok");

        ResultUtil res = service.followFriendIntoRoomAsSpectator(1, "me", 2);
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        assertThat(res.getData().get("roomId")).isEqualTo("room-x");
        assertThat(res.getData().get("observeNote")).isEqualTo("ok");
    }

    @Test
    void getFriendSpectateRoomContext_returnsRoomIdAlignedWithInvitePayload() {
        when(friendLinkMapper.selectCount(any())).thenReturn(1L);

        DpUser bob = new DpUser();
        bob.setId(2);
        bob.setNickname("Bob");
        when(dpUserMapper.selectById(2)).thenReturn(bob);

        when(dpRoomService.findActiveRoomIdForNickname("Bob")).thenReturn("room-x");
        when(dpRoomService.dpRoomExistsInMemory("room-x")).thenReturn(true);
        when(dpRoomService.canActiveMemberInviteFriends(eq("room-x"), eq("Bob"))).thenReturn(true);

        ResultUtil res = service.getFriendSpectateRoomContext(1, 2);
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        assertThat(res.getData().get("roomId")).isEqualTo("room-x");
    }
}
