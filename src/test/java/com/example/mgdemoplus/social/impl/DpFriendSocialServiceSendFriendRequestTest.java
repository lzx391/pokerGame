package com.example.mgdemoplus.social.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.mgdemoplus.social.entity.DpFriendLinkRow;
import com.example.mgdemoplus.social.entity.DpFriendRequestRow;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import com.example.mgdemoplus.social.mapper.DpFriendLinkMapper;
import com.example.mgdemoplus.social.mapper.DpFriendRequestMapper;
import com.example.mgdemoplus.roomchat.mapper.DpRoomInviteMapper;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.presence.DpSitePresenceService;
import com.example.mgdemoplus.social.notify.SocialNotifyPublisher;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * sendFriendRequest：ACCEPTED 无 link 时校正为 PENDING 并重发；有 link 仍报已是好友。
 */
@ExtendWith(MockitoExtension.class)
class DpFriendSocialServiceSendFriendRequestTest {

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

    @Spy
    @InjectMocks
    private DpFriendSocialService service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, DpFriendRequestRow.class);
        TableInfoHelper.initTableInfo(assistant, DpFriendLinkRow.class);
    }

    @BeforeEach
    void skipRoomInviteExpiry() {
        lenient().doNothing().when(service).touchExpireRoomInvites();
    }

    @Test
    void sendFriendRequest_acceptedWithoutLink_resetsToPendingAndOk() {
        when(friendLinkMapper.selectCount(any())).thenReturn(0L);

        DpFriendRequestRow accepted = new DpFriendRequestRow();
        accepted.setFromUserId(1);
        accepted.setToUserId(2);
        accepted.setStatus("ACCEPTED");
        when(friendRequestMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(accepted);

        ResultUtil res = service.sendFriendRequest(1, 2);

        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        assertThat(res.getData().get("message")).isEqualTo("已发送申请");

        verify(friendRequestMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void sendFriendRequest_acceptedWithLink_returnsAlreadyFriends() {
        when(friendLinkMapper.selectCount(any())).thenReturn(1L);

        ResultUtil res = service.sendFriendRequest(1, 2);

        assertThat(Boolean.TRUE.equals(res.getSuccess())).isFalse();
        assertThat(res.getData().get("message")).isEqualTo("已是好友");
    }
}
