package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.dp.presence.DpFriendPresenceState;
import com.example.mgdemoplus.entity.dp.DpFriendLinkRow;
import com.example.mgdemoplus.mapper.dp.DpFriendLinkMapper;
import com.example.mgdemoplus.mapper.dp.DpFriendRequestMapper;
import com.example.mgdemoplus.mapper.dp.DpRoomInviteMapper;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.example.mgdemoplus.service.dp.DpFriendPresenceService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GET /dp/friends：好友均来自 {@code dp_friend_link}（互为好友）；presence 仅对该集合批量填充。
 */
@ExtendWith(MockitoExtension.class)
class DpFriendSocialServiceListFriendsPresenceTest {

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

    @InjectMocks
    private DpFriendSocialService service;

    @Test
    void listFriends_attachesPresenceStringsForEachFriendRow() {
        when(roomInviteMapper.update(any(), any())).thenReturn(0);

        DpFriendLinkRow r1 = new DpFriendLinkRow();
        r1.setFriendUserId(10);
        r1.setFriendNickname("Alpha");
        DpFriendLinkRow r2 = new DpFriendLinkRow();
        r2.setFriendUserId(20);
        r2.setFriendNickname("Beta");
        when(friendLinkMapper.listFriendsOfUser(1)).thenReturn(List.of(r1, r2));

        when(friendPresenceService.getEffectiveMany(any()))
                .thenReturn(
                        new LinkedHashMap<>(
                                Map.of(
                                        10, DpFriendPresenceState.IN_GAME,
                                        20, DpFriendPresenceState.IDLE)));

        ResultUtil res = service.listFriends(1);
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> friends = (List<Map<String, Object>>) res.getData().get("friends");
        assertThat(friends).hasSize(2);
        assertThat(friends.get(0).get("presence")).isEqualTo("IN_GAME");
        assertThat(friends.get(1).get("presence")).isEqualTo("IDLE");
        assertThat(friends.get(0).get("friendship_status")).isEqualTo("ACCEPTED");
    }
}
