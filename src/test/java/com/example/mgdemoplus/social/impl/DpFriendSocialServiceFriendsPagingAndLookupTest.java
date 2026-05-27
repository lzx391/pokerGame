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
import java.util.List;
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
    void resolveUsersForExactLookup_numericId_usesSelectByIdAndNickname() {
        DpUser u = new DpUser();
        u.setId(7);
        u.setNickname("Seven");
        when(dpUserMapper.selectById(7)).thenReturn(u);
        when(dpUserMapper.selectByNickname("7")).thenReturn(null);

        List<DpUser> found = DpFriendSocialService.resolveUsersForExactLookup("7", dpUserMapper);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(7);
    }

    @Test
    void resolveUsersForExactLookup_numeric_returnsBothWhenIdAndNicknameDiffer() {
        DpUser byId = new DpUser();
        byId.setId(11);
        byId.setNickname("Eleven");
        DpUser byNick = new DpUser();
        byNick.setId(99);
        byNick.setNickname("11");
        when(dpUserMapper.selectById(11)).thenReturn(byId);
        when(dpUserMapper.selectByNickname("11")).thenReturn(byNick);

        List<DpUser> found = DpFriendSocialService.resolveUsersForExactLookup("11", dpUserMapper);
        assertThat(found).hasSize(2);
        assertThat(found.get(0).getId()).isEqualTo(11);
        assertThat(found.get(1).getId()).isEqualTo(99);
    }

    @Test
    void resolveUsersForExactLookup_sameUserIdAndNickname_dedupesToOne() {
        DpUser u = new DpUser();
        u.setId(11);
        u.setNickname("11");
        DpUser sameByNick = new DpUser();
        sameByNick.setId(11);
        sameByNick.setNickname("11");
        when(dpUserMapper.selectById(11)).thenReturn(u);
        when(dpUserMapper.selectByNickname("11")).thenReturn(sameByNick);

        List<DpUser> found = DpFriendSocialService.resolveUsersForExactLookup("11", dpUserMapper);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(11);
    }

    @Test
    void resolveUsersForExactLookup_nickname_usesSelectByNickname() {
        DpUser u = new DpUser();
        u.setId(3);
        u.setNickname("ExactNick");
        when(dpUserMapper.selectByNickname("ExactNick")).thenReturn(u);

        List<DpUser> found = DpFriendSocialService.resolveUsersForExactLookup("ExactNick", dpUserMapper);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getNickname()).isEqualTo("ExactNick");
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
        when(dpUserMapper.selectByNickname("50")).thenReturn(null);

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
        List<Map<String, Object>> items = (List<Map<String, Object>>) res.getData().get("items");
        assertThat(items).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) items.get(0).get("user");
        assertThat(user.get("userId")).isEqualTo(9);
        assertThat(user.get("nickname")).isEqualTo("PlayerNine");
        assertThat(user.get("avatarUrl")).isEqualTo("/a.png");
        assertThat(user).doesNotContainKey("password");
        assertThat(items.get(0).get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_CAN_ADD);
    }

    @Test
    void lookupUserForFriendAdd_selfStatus() {
        DpUser me = new DpUser();
        me.setId(1);
        me.setNickname("Me");
        when(dpUserMapper.selectById(1)).thenReturn(me);
        when(dpUserMapper.selectByNickname("1")).thenReturn(null);

        ResultUtil res = service.lookupUserForFriendAdd(1, "1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) res.getData().get("items");
        assertThat(items.get(0).get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_SELF);
    }

    @Test
    void lookupUserForFriendAdd_pendingOutbound() {
        DpUser target = new DpUser();
        target.setId(2);
        target.setNickname("Peer");
        when(dpUserMapper.selectById(2)).thenReturn(target);
        when(dpUserMapper.selectByNickname("2")).thenReturn(null);
        when(friendLinkMapper.selectCount(any())).thenReturn(0L);
        DpFriendRequestRow pending = new DpFriendRequestRow();
        pending.setStatus("PENDING");
        when(friendRequestMapper.selectOne(any()))
                .thenReturn(pending)
                .thenReturn(null);

        ResultUtil res = service.lookupUserForFriendAdd(1, "2");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) res.getData().get("items");
        assertThat(items.get(0).get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_PENDING_OUTBOUND);
    }

    @Test
    void lookupUserForFriendAdd_dualMatch_returnsTwoItems() {
        DpUser byId = new DpUser();
        byId.setId(11);
        byId.setNickname("Eleven");
        DpUser byNick = new DpUser();
        byNick.setId(99);
        byNick.setNickname("11");
        when(dpUserMapper.selectById(11)).thenReturn(byId);
        when(dpUserMapper.selectByNickname("11")).thenReturn(byNick);
        when(friendLinkMapper.selectCount(any())).thenReturn(0L);
        when(friendRequestMapper.selectOne(any())).thenReturn(null);

        ResultUtil res = service.lookupUserForFriendAdd(1, "11");
        assertThat(Boolean.TRUE.equals(res.getSuccess())).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) res.getData().get("items");
        assertThat(items).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstUser = (Map<String, Object>) items.get(0).get("user");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondUser = (Map<String, Object>) items.get(1).get("user");
        assertThat(firstUser.get("userId")).isEqualTo(11);
        assertThat(secondUser.get("userId")).isEqualTo(99);
        assertThat(items.get(0).get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_CAN_ADD);
        assertThat(items.get(1).get("addStatus")).isEqualTo(DpFriendSocialService.ADD_STATUS_CAN_ADD);
    }
}
