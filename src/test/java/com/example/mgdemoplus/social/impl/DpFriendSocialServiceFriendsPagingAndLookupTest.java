package com.example.mgdemoplus.social.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.social.entity.DpFriendLinkRow;
import com.example.mgdemoplus.social.entity.DpFriendRequestRow;
import com.example.mgdemoplus.social.mapper.DpFriendLinkMapper;
import com.example.mgdemoplus.social.mapper.DpFriendRequestMapper;
import com.example.mgdemoplus.roomchat.mapper.DpRoomInviteMapper;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.presence.DpSitePresenceService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 好友列表分页/筛选与加好友精确查人（smoke）。
 */
@ExtendWith(MockitoExtension.class)
class DpFriendSocialServiceFriendsPagingAndLookupTest {

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

    @Spy
    @InjectMocks
    private DpFriendSocialService service;

    @BeforeEach
    void skipRoomInviteExpiry() {
        lenient().doNothing().when(service).touchExpireRoomInvites();
    }

    @org.junit.jupiter.api.AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
    }

    @Test
    void parseFriendListFilter_pureDigits_filtersByFriendUserId() {
        assertThat(DpFriendSocialService.parseFriendListFilter("42").filterFriendUserId()).isEqualTo(42);
        assertThat(DpFriendSocialService.parseFriendListFilter("42").nicknameContains()).isNull();
    }

    @Test
    void parseFriendListFilter_nonNumeric_filtersNicknameContains() {
        assertThat(DpFriendSocialService.parseFriendListFilter("alice").filterFriendUserId()).isNull();
        assertThat(DpFriendSocialService.parseFriendListFilter("alice").nicknameContains()).isEqualTo("alice");
    }

    @Test
    void parseFriendListFilter_blank_noFilter() {
        assertThat(DpFriendSocialService.parseFriendListFilter("  ").filterFriendUserId()).isNull();
        assertThat(DpFriendSocialService.parseFriendListFilter("  ").nicknameContains()).isNull();
    }

    @Test
    void listFriends_passesNumericQAsFriendIdFilter() {
        when(friendLinkMapper.listFriendsOfUserPaged(eq(1), eq(99), isNull()))
                .thenReturn(Collections.emptyList());
        when(friendPresenceService.getEffectiveMany(any())).thenReturn(Collections.emptyMap());

        ResultUtil res = service.listFriends(1, 2, 50, "99");
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        Page<?> page = PageHelper.getLocalPage();
        assertThat(page).isNotNull();
        assertThat(page.getPageNum()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(50);

        ArgumentCaptor<Integer> idCap = ArgumentCaptor.forClass(Integer.class);
        verify(friendLinkMapper).listFriendsOfUserPaged(eq(1), idCap.capture(), isNull());
        assertThat(idCap.getValue()).isEqualTo(99);
    }

    @Test
    void listFriends_passesNicknameQToMapper() {
        when(friendLinkMapper.listFriendsOfUserPaged(eq(1), isNull(), eq("vivo")))
                .thenReturn(Collections.emptyList());
        when(friendPresenceService.getEffectiveMany(any())).thenReturn(Collections.emptyMap());

        service.listFriends(1, 1, 20, "vivo");

        verify(friendLinkMapper).listFriendsOfUserPaged(eq(1), isNull(), eq("vivo"));
    }

    @Test
    void listFriends_clampsPageSizeToMax100() {
        when(friendLinkMapper.listFriendsOfUserPaged(eq(1), isNull(), isNull()))
                .thenReturn(Collections.emptyList());
        when(friendPresenceService.getEffectiveMany(any())).thenReturn(Collections.emptyMap());

        service.listFriends(1, 1, 500, null);
        Page<?> page = PageHelper.getLocalPage();
        assertThat(page).isNotNull();
        assertThat(page.getPageSize()).isEqualTo(100);
    }

    @Test
    void resolveUserForExactLookup_numericId_usesSelectByIdOnly() {
        DpUser u = new DpUser();
        u.setId(7);
        u.setNickname("Seven");
        when(dpUserMapper.selectById(7)).thenReturn(u);

        DpUser found = DpFriendSocialService.resolveUserForExactLookup("7", dpUserMapper);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(7);
        verify(dpUserMapper).selectById(7);
    }

    @Test
    void resolveUserForExactLookup_nickname_usesSelectByNickname() {
        DpUser u = new DpUser();
        u.setId(3);
        u.setNickname("ExactNick");
        when(dpUserMapper.selectByNickname("ExactNick")).thenReturn(u);

        DpUser found = DpFriendSocialService.resolveUserForExactLookup("ExactNick", dpUserMapper);
        assertThat(found).isNotNull();
        assertThat(found.getNickname()).isEqualTo("ExactNick");
    }

    @Test
    void lookupUserForFriendAdd_notFound() {
        when(dpUserMapper.selectByNickname("nobody")).thenReturn(null);

        ResultUtil res = service.lookupUserForFriendAdd(1, "nobody");
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isFalse();
        assertThat(res.getData().get("message")).isEqualTo("用户不存在");
    }

    @Test
    void lookupUserForFriendAdd_botRejected() {
        DpUser bot = new DpUser();
        bot.setId(50);
        bot.setNickname("BOT_TAG_1");
        when(dpUserMapper.selectById(50)).thenReturn(bot);

        ResultUtil res = service.lookupUserForFriendAdd(1, "50");
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isFalse();
        assertThat(res.getData().get("message")).isEqualTo("不可添加");
    }

    @Test
    void lookupUserForFriendAdd_returnsPublicFieldsAndCanAdd() {
        DpUser target = new DpUser();
        target.setId(9);
        target.setNickname("PlayerNine");
        target.setAvatarUrl("/a.png");
        when(dpUserMapper.selectByNickname("PlayerNine")).thenReturn(target);
        when(friendLinkMapper.selectCount(any())).thenReturn(0L);
        when(friendRequestMapper.selectOne(any())).thenReturn(null);

        ResultUtil res = service.lookupUserForFriendAdd(1, "PlayerNine");
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) res.getData().get("user");
        assertThat(user.get("userId")).isEqualTo(9);
        assertThat(user.get("nickname")).isEqualTo("PlayerNine");
        assertThat(user.get("avatarUrl")).isEqualTo("/a.png");
        assertThat(user).doesNotContainKey("password");
        assertThat(res.getData().get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_CAN_ADD);
    }

    @Test
    void lookupUserForFriendAdd_selfStatus() {
        DpUser me = new DpUser();
        me.setId(1);
        me.setNickname("Me");
        when(dpUserMapper.selectById(1)).thenReturn(me);

        ResultUtil res = service.lookupUserForFriendAdd(1, "1");
        assertThat(res.getData().get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_SELF);
    }

    @Test
    void lookupUserForFriendAdd_pendingOutbound() {
        DpUser target = new DpUser();
        target.setId(2);
        target.setNickname("Peer");
        when(dpUserMapper.selectById(2)).thenReturn(target);
        when(friendLinkMapper.selectCount(any())).thenReturn(0L);
        DpFriendRequestRow pending = new DpFriendRequestRow();
        pending.setStatus("PENDING");
        when(friendRequestMapper.selectOne(any()))
                .thenReturn(pending)
                .thenReturn(null);

        ResultUtil res = service.lookupUserForFriendAdd(1, "2");
        assertThat(res.getData().get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_PENDING_OUTBOUND);
    }
}
