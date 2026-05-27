package com.example.mgdemoplus.social.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.mgdemoplus.social.entity.DpFriendLinkRow;
import com.example.mgdemoplus.social.entity.DpFriendRequestRow;
import com.example.mgdemoplus.roomchat.entity.DpRoomInviteRow;
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
 * removeFriend：删 link 同时将双向 ACCEPTED 申请标 EXPIRED；delete 返回 0 仍 expire。
 */
@ExtendWith(MockitoExtension.class)
class DpFriendSocialServiceRemoveFriendTest {

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
        TableInfoHelper.initTableInfo(assistant, DpRoomInviteRow.class);
        TableInfoHelper.initTableInfo(assistant, DpFriendLinkRow.class);
    }

    @BeforeEach
    void skipRoomInviteExpiry() {
        lenient().doNothing().when(service).touchExpireRoomInvites();
    }

    @Test
    void removeFriend_whenLinkDeleted_expiresAcceptedRequestsBidirectionally() {
        when(friendLinkMapper.delete(any())).thenReturn(1);

        ResultUtil res = service.removeFriend(10, 20);

        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        assertThat(res.getData().get("message")).isEqualTo("已解除好友关系");

        verify(friendRequestMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void removeFriend_whenLinkDeleteReturnsZero_stillExpiresAcceptedRequests() {
        when(friendLinkMapper.delete(any())).thenReturn(0);

        ResultUtil res = service.removeFriend(10, 20);

        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        assertThat(res.getData().get("message")).isEqualTo("未找到好友关系");

        verify(friendRequestMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }
}
