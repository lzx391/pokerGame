package com.example.mgdemoplus.social.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mgdemoplus.social.entity.DpFriendLinkRow;
import com.example.mgdemoplus.social.entity.DpFriendRequestRow;
import com.example.mgdemoplus.roomchat.entity.DpRoomInviteRow;
import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.social.mapper.DpFriendLinkMapper;
import com.example.mgdemoplus.social.mapper.DpFriendRequestMapper;
import com.example.mgdemoplus.roomchat.mapper.DpRoomInviteMapper;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.presence.DpFriendPresenceState;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.presence.DpSitePresenceService;
import com.example.mgdemoplus.social.notify.SocialNotifyPublisher;
import com.example.mgdemoplus.utils.DpDateTimeSupport;
import com.example.mgdemoplus.utils.ResultUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DP 好友双向申请、局内成员进房邀约（邮箱）；写操作依赖调用方传入的当前用户 id（由 Controller 从 JWT 昵称解析），
 * 与请求体中的 targetUserId 隔离防越权。
 */
@Service
public class DpFriendSocialService {

    private static final Logger log = LoggerFactory.getLogger(DpFriendSocialService.class);

    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final String EXPIRED = "EXPIRED";
    private static final String CANCELLED = "CANCELLED";

    private static final int DEFAULT_FRIENDS_PAGE_SIZE = 20;
    private static final int MAX_FRIENDS_PAGE_SIZE = 100;

    /** 加好友查人：可发起申请 */
    public static final String ADD_STATUS_CAN_ADD = "CAN_ADD";
    /** 查到自己 */
    public static final String ADD_STATUS_SELF = "SELF";
    /** 已是好友 */
    public static final String ADD_STATUS_ALREADY_FRIENDS = "ALREADY_FRIENDS";
    /** 我已向对方发出待处理申请 */
    public static final String ADD_STATUS_PENDING_OUTBOUND = "PENDING_OUTBOUND";
    /** 对方向我发出待处理申请 */
    public static final String ADD_STATUS_PENDING_INBOUND = "PENDING_INBOUND";

    @Autowired
    private DpFriendRequestMapper friendRequestMapper;
    @Autowired
    private DpFriendLinkMapper friendLinkMapper;
    @Autowired
    private DpRoomInviteMapper roomInviteMapper;
    @Autowired
    private DpUserMapper dpUserMapper;
    @Autowired
    private DpRoomService dpRoomService;
    @Autowired
    private DpFriendPresenceService friendPresenceService;
    @Autowired
    private DpSitePresenceService sitePresenceService;
    @Autowired
    @Lazy
    private SocialNotifyPublisher socialNotifyPublisher;

    /** 将已到期的进房邀请标为 EXPIRED（列表/计数/处理前调用，不推 SSE）。 */
    public void touchExpireRoomInvites() {
        expireRoomInvitesSilent();
    }

    /** 过期清理后对受影响被邀请人推送 mailbox 未读变化（SSE 心跳等调用）。 */
    public void expireDueRoomInvitesAndNotify() {
        List<Integer> invitees = roomInviteMapper.listInviteeUserIdsWithPendingExpired();
        if (invitees == null || invitees.isEmpty()) {
            return;
        }
        expireRoomInvitesSilent();
        for (Integer uid : invitees) {
            pushSocialNotify(uid);
        }
    }

    /** 与 {@link #unreadCount} / {@code GET /dp/mailbox/unread-count} 口径一致。 */
    public int mailboxUnreadCount(int currentUserId) {
        touchExpireRoomInvites();
        return countPendingFriendRequestsToUser(currentUserId)
                + countPendingRoomInvitesToUserFresh(currentUserId);
    }

    private void expireRoomInvitesSilent() {
        roomInviteMapper.update(
                null,
                new LambdaUpdateWrapper<DpRoomInviteRow>()
                        .set(DpRoomInviteRow::getStatus, EXPIRED)
                        .eq(DpRoomInviteRow::getStatus, PENDING)
                        .apply("expires_at <= NOW(3)"));
    }

    private void pushSocialNotify(int userId) {
        if (userId > 0) {
            log.info("[social-sse] pushSocialNotify userId={}", userId);
            socialNotifyPublisher.notifyUser(userId);
        } else {
            log.warn("[social-sse] pushSocialNotify skipped invalid userId={}", userId);
        }
    }

    public ResultUtil sendFriendRequest(int fromUserId, int toUserId) {
        if (toUserId <= 0 || fromUserId <= 0) {
            return ResultUtil.error().data("message", "用户 id 无效");
        }
        if (fromUserId == toUserId) {
            return ResultUtil.error().data("message", "不能向自己发送好友申请");
        }
        int low = Math.min(fromUserId, toUserId);
        int high = Math.max(fromUserId, toUserId);
        if (countFriendLinksBetween(low, high) > 0) {
            return ResultUtil.error().data("message", "已是好友");
        }

        DpFriendRequestRow reverse = selectFriendRequestByPair(toUserId, fromUserId);
        if (reverse != null && PENDING.equals(reverse.getStatus())) {
            return ResultUtil.error().data("message", "对方已向您发来申请，请先在邮箱处理");
        }

        DpFriendRequestRow row = selectFriendRequestByPair(fromUserId, toUserId);
        if (row == null) {
            insertFriendRequest(fromUserId, toUserId);
            pushSocialNotify(toUserId);
            return ResultUtil.ok().data("message", "已发送申请");
        }
        if (PENDING.equals(row.getStatus())) {
            return ResultUtil.ok().data("message", "申请已存在");
        }
        if (ACCEPTED.equals(row.getStatus())) {
            if (countFriendLinksBetween(low, high) > 0) {
                return ResultUtil.error().data("message", "已是好友");
            }
            friendRequestMapper.update(
                    null,
                    new LambdaUpdateWrapper<DpFriendRequestRow>()
                            .set(DpFriendRequestRow::getStatus, PENDING)
                            .set(DpFriendRequestRow::getCreatedAt, LocalDateTime.now())
                            .eq(DpFriendRequestRow::getFromUserId, fromUserId)
                            .eq(DpFriendRequestRow::getToUserId, toUserId));
            pushSocialNotify(toUserId);
            return ResultUtil.ok().data("message", "已发送申请");
        }
        if (REJECTED.equals(row.getStatus()) || EXPIRED.equals(row.getStatus())) {
            friendRequestMapper.update(
                    null,
                    new LambdaUpdateWrapper<DpFriendRequestRow>()
                            .set(DpFriendRequestRow::getStatus, PENDING)
                            .set(DpFriendRequestRow::getCreatedAt, LocalDateTime.now())
                            .eq(DpFriendRequestRow::getFromUserId, fromUserId)
                            .eq(DpFriendRequestRow::getToUserId, toUserId));
            pushSocialNotify(toUserId);
            return ResultUtil.ok().data("message", "已发送申请");
        }
        return ResultUtil.error().data("message", "申请状态不可用");
    }

    public ResultUtil listPendingFriendRequestsInbound(int currentUserId) {
        touchExpireRoomInvites();
        List<DpFriendRequestRow> rows = friendRequestMapper.listPendingFriendRequestsToUser(currentUserId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DpFriendRequestRow r : rows) {
            out.add(toFriendRequestPayload(r));
        }
        return ResultUtil.ok().data("items", out);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResultUtil acceptFriendRequest(int currentUserId, long requestId) {
        touchExpireRoomInvites();
        DpFriendRequestRow row = friendRequestMapper.selectById(requestId);
        if (row == null) {
            return ResultUtil.error().data("message", "申请不存在");
        }
        if (row.getToUserId() != currentUserId) {
            return ResultUtil.error().data("message", "无权处理该申请");
        }
        if (ACCEPTED.equals(row.getStatus())) {
            int low = Math.min(row.getFromUserId(), row.getToUserId());
            int high = Math.max(row.getFromUserId(), row.getToUserId());
            if (countFriendLinksBetween(low, high) > 0) {
                return ResultUtil.ok().data("message", "已同意过了");
            }
            insertFriendLink(low, high);
            pushSocialNotify(currentUserId);
            return ResultUtil.ok().data("message", "已同意并成为好友");
        }
        if (!PENDING.equals(row.getStatus())) {
            return ResultUtil.ok().data("message", "申请已失效或已处理");
        }
        int low = Math.min(row.getFromUserId(), row.getToUserId());
        int high = Math.max(row.getFromUserId(), row.getToUserId());
        int n = updateFriendRequestStatus(requestId, PENDING, ACCEPTED);
        if (n != 1) {
            return ResultUtil.error().data("message", "并发更新失败，请重试");
        }
        if (countFriendLinksBetween(low, high) == 0) {
            insertFriendLink(low, high);
        }
        pushSocialNotify(currentUserId);
        return ResultUtil.ok().data("message", "已同意并成为好友");
    }

    public ResultUtil rejectFriendRequest(int currentUserId, long requestId) {
        touchExpireRoomInvites();
        DpFriendRequestRow row = friendRequestMapper.selectById(requestId);
        if (row == null) {
            return ResultUtil.error().data("message", "申请不存在");
        }
        if (row.getToUserId() != currentUserId) {
            return ResultUtil.error().data("message", "无权处理该申请");
        }
        if (REJECTED.equals(row.getStatus())) {
            return ResultUtil.ok().data("message", "已拒绝过了");
        }
        if (!PENDING.equals(row.getStatus())) {
            return ResultUtil.ok().data("message", "申请已无法拒绝");
        }
        int u = updateFriendRequestStatus(requestId, PENDING, REJECTED);
        if (u == 0) {
            row = friendRequestMapper.selectById(requestId);
            if (row != null && REJECTED.equals(row.getStatus())) {
                return ResultUtil.ok().data("message", "已拒绝过了");
            }
            return ResultUtil.error().data("message", "更新失败");
        }
        pushSocialNotify(currentUserId);
        return ResultUtil.ok().data("message", "已拒绝");
    }

    /**
     * 分页好友列表：筛选 {@code q}（纯数字→好友 id；否则昵称包含，不区分大小写）；
     * 按最近私信时间降序（无消息则按成为好友时间），再昵称升序。
     */
    public ResultUtil listFriends(int currentUserId, int page, int pageSize, String q) {
        touchExpireRoomInvites();
        int safePage = Math.max(page, 1);
        int safeSize = pageSize > 0 ? pageSize : DEFAULT_FRIENDS_PAGE_SIZE;
        safeSize = Math.min(safeSize, MAX_FRIENDS_PAGE_SIZE);

        FriendListFilter filter = parseFriendListFilter(q);

        // PageHelper方式
        PageHelper.startPage(safePage, safeSize);

        List<DpFriendLinkRow> rows = friendLinkMapper.listFriendsOfUserPaged(
            currentUserId,
            filter.filterFriendUserId(),
            filter.nicknameContains()
        );

        PageInfo<DpFriendLinkRow> pageInfo = new PageInfo<>(rows);

        List<Map<String, Object>> items = buildFriendListItems(currentUserId, pageInfo.getList());
        return ResultUtil.ok()
                .data("friends", items)
                .data("total", pageInfo.getTotal())
                .data("page", pageInfo.getPageNum())
                .data("pageSize", pageInfo.getPageSize());
    }

    /**
     * 加好友前精确查人：纯数字且 &gt;0 → {@link DpUserMapper#selectById} 并再 {@link DpUserMapper#selectByNickname} 全等；
     * 非纯数字仅昵称全等。返回 {@code items}，每项含公开资料与 {@code addStatus}；机器人跳过，全为机器人则不可添加。
     */
    public ResultUtil lookupUserForFriendAdd(int currentUserId, String q) {
        touchExpireRoomInvites();
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.isEmpty()) {
            return ResultUtil.error().data("message", "请输入用户 id 或昵称");
        }
        List<DpUser> candidates = resolveUsersForExactLookup(trimmed, dpUserMapper);
        List<Map<String, Object>> items = new ArrayList<>();
        for (DpUser target : candidates) {
            if (DpNpcEngine.isBotNickname(target.getNickname())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("user", toPublicUserPayload(target));
            item.put("addStatus", resolveFriendAddStatus(currentUserId, target.getId()));
            items.add(item);
        }
        if (items.isEmpty()) {
            if (!candidates.isEmpty()) {
                return ResultUtil.error().data("message", "不可添加");
            }
            return ResultUtil.error().data("message", "用户不存在");
        }
        return ResultUtil.ok().data("items", items);
    }

    static FriendListFilter parseFriendListFilter(String rawQ) {
        String trimmed = rawQ == null ? "" : rawQ.trim();
        if (trimmed.isEmpty()) {
            return new FriendListFilter(null, null);
        }
        if (trimmed.matches("\\d+")) {
            long id = Long.parseLong(trimmed);
            if (id > 0 && id <= Integer.MAX_VALUE) {
                return new FriendListFilter((int) id, null);
            }
        }
        return new FriendListFilter(null, trimmed);
    }

    static List<DpUser> resolveUsersForExactLookup(String trimmedQ, DpUserMapper userMapper) {
        Map<Integer, DpUser> byId = new LinkedHashMap<>();
        if (trimmedQ.matches("\\d+")) {
            long id = Long.parseLong(trimmedQ);
            if (id > 0 && id <= Integer.MAX_VALUE) {
                DpUser found = userMapper.selectById((int) id);
                if (found != null) {
                    byId.put(found.getId(), found);
                }
            }
        }
        DpUser byNickname = userMapper.selectByNickname(trimmedQ);
        if (byNickname != null) {
            byId.putIfAbsent(byNickname.getId(), byNickname);
        }
        return new ArrayList<>(byId.values());
    }

    record FriendListFilter(Integer filterFriendUserId, String nicknameContains) {}

    private String resolveFriendAddStatus(int currentUserId, int targetUserId) {
        if (currentUserId == targetUserId) {
            return ADD_STATUS_SELF;
        }
        int low = Math.min(currentUserId, targetUserId);
        int high = Math.max(currentUserId, targetUserId);
        if (countFriendLinksBetween(low, high) > 0) {
            return ADD_STATUS_ALREADY_FRIENDS;
        }
        DpFriendRequestRow outbound = selectFriendRequestByPair(currentUserId, targetUserId);
        if (outbound != null && PENDING.equals(outbound.getStatus())) {
            return ADD_STATUS_PENDING_OUTBOUND;
        }
        DpFriendRequestRow inbound = selectFriendRequestByPair(targetUserId, currentUserId);
        if (inbound != null && PENDING.equals(inbound.getStatus())) {
            return ADD_STATUS_PENDING_INBOUND;
        }
        return ADD_STATUS_CAN_ADD;
    }

    private static Map<String, Object> toPublicUserPayload(DpUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("avatarUpdatedAt", DpDateTimeSupport.toEpochMilli(u.getAvatarUpdatedAt()));
        return m;
    }

    private List<Map<String, Object>> buildFriendListItems(int currentUserId, List<DpFriendLinkRow> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Integer> presenceEligibleFriendIds = new ArrayList<>();
        Set<Integer> seenFriendIds = new LinkedHashSet<>();
        for (DpFriendLinkRow fl : rows) {
            Integer fid = fl.getFriendUserId();
            if (fid == null || fid <= 0 || fid == currentUserId) {
                continue;
            }
            if (!seenFriendIds.add(fid)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", fid);
            m.put("nickname", fl.getFriendNickname());
            m.put("avatarUrl", fl.getFriendAvatarUrl());
            m.put("avatarUpdatedAt", DpDateTimeSupport.toEpochMilli(fl.getFriendAvatarUpdatedAt()));
            m.put("friendship_status", ACCEPTED);
            items.add(m);
            presenceEligibleFriendIds.add(fid);
        }
        Map<Integer, DpFriendPresenceState> presenceByFriend =
                friendPresenceService.getEffectiveMany(presenceEligibleFriendIds);
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> m = items.get(i);
            if (!ACCEPTED.equals(m.get("friendship_status"))) {
                continue;
            }
            Integer fid = presenceEligibleFriendIds.get(i);
            DpFriendPresenceState inRoom = presenceByFriend.get(fid);
            if (inRoom == null) {
                inRoom = DpFriendPresenceState.IDLE;
            }
            DpFriendPresenceState display =
                    resolveFriendListPresence(inRoom, sitePresenceService.isOnlineSite(fid));
            m.put("presence", display.name());
        }
        return items;
    }

    /**
     * 房内 {@link DpFriendPresenceState#IN_GAME} 优先；否则站点在线 → {@link DpFriendPresenceState#IDLE}，
     * 否则 {@link DpFriendPresenceState#OFFLINE}。
     */
    static DpFriendPresenceState resolveFriendListPresence(
            DpFriendPresenceState inRoomPresence, boolean onlineSite) {
        if (inRoomPresence == DpFriendPresenceState.IN_GAME) {
            return DpFriendPresenceState.IN_GAME;
        }
        if (onlineSite) {
            return DpFriendPresenceState.IDLE;
        }
        return DpFriendPresenceState.OFFLINE;
    }

    /**
     * 解除互为好友：删除无序 friend_link，将双方 PENDING 进房邀请置为 CANCELLED，
     * 并将双向 ACCEPTED 好友申请置为 EXPIRED（以 link 为准，无好友即清错误接受态）；幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultUtil removeFriend(int currentUserId, int friendUserId) {
        touchExpireRoomInvites();
        if (friendUserId <= 0 || currentUserId <= 0) {
            return ResultUtil.error().data("message", "用户 id 无效");
        }
        if (currentUserId == friendUserId) {
            return ResultUtil.error().data("message", "不能删除自己");
        }
        int lo = Math.min(currentUserId, friendUserId);
        int hi = Math.max(currentUserId, friendUserId);
        cancelPendingRoomInvitesBetweenUsers(currentUserId, friendUserId);
        expireAcceptedFriendRequestsBetweenUsers(currentUserId, friendUserId);
        int removed = friendLinkMapper.delete(
                new LambdaQueryWrapper<DpFriendLinkRow>()
                        .apply(
                                "(user_low_id = {0} AND user_high_id = {1}) OR (user_low_id = {1} AND user_high_id = {0})",
                                lo,
                                hi));
        if (removed > 0) {
            pushSocialNotify(currentUserId);
            pushSocialNotify(friendUserId);
            return ResultUtil.ok().data("message", "已解除好友关系");
        }
        return ResultUtil.ok().data("message", "未找到好友关系");
    }

    public ResultUtil createRoomInvite(int inviterUserId, String inviterNickname, String roomId, int inviteeUserId) {
        log.info(
                "[social-sse] createRoomInvite request inviterUserId={} inviteeUserId={} roomId={}",
                inviterUserId,
                inviteeUserId,
                roomId);
        touchExpireRoomInvites();
        if (inviterUserId == inviteeUserId) {
            log.warn("[social-sse] createRoomInvite rejected: self invite");
            return ResultUtil.error().data("message", "不能邀请自己");
        }
        if (roomId == null || roomId.isBlank()) {
            log.warn("[social-sse] createRoomInvite rejected: empty roomId");
            return ResultUtil.error().data("message", "room_id 不能为空");
        }
        if (!dpRoomService.dpRoomExistsInMemory(roomId)) {
            log.warn("[social-sse] createRoomInvite rejected: room not in memory roomId={}", roomId);
            return ResultUtil.error().data("message", "房间不存在");
        }
        if (!dpRoomService.canActiveMemberInviteFriends(roomId, inviterNickname)) {
            log.warn(
                    "[social-sse] createRoomInvite rejected: inviter cannot invite roomId={} nickname={}",
                    roomId,
                    inviterNickname);
            return ResultUtil.error().data("message", "仅局内未离座成员或观众可发起进房邀请");
        }
        DpUser invitee = dpUserMapper.selectById(inviteeUserId);
        if (invitee == null) {
            log.warn("[social-sse] createRoomInvite rejected: invitee not found inviteeUserId={}", inviteeUserId);
            return ResultUtil.error().data("message", "被邀请用户不存在");
        }

        int low = Math.min(inviterUserId, inviteeUserId);
        int high = Math.max(inviterUserId, inviteeUserId);
        if (countFriendLinksBetween(low, high) == 0) {
            log.warn(
                    "[social-sse] createRoomInvite rejected: not friends inviter={} invitee={}",
                    inviterUserId,
                    inviteeUserId);
            return ResultUtil.error().data("message", "仅互为好友可邀请");
        }

        roomInviteMapper.delete(
                new LambdaQueryWrapper<DpRoomInviteRow>()
                        .eq(DpRoomInviteRow::getInviterUserId, inviterUserId)
                        .eq(DpRoomInviteRow::getInviteeUserId, inviteeUserId)
                        .eq(DpRoomInviteRow::getRoomId, roomId.trim())
                        .eq(DpRoomInviteRow::getStatus, PENDING));
        LocalDateTime exp = LocalDateTime.now().plusSeconds(60);
        DpRoomInviteRow invite = new DpRoomInviteRow();
        invite.setInviterUserId(inviterUserId);
        invite.setInviteeUserId(inviteeUserId);
        invite.setRoomId(roomId.trim());
        invite.setStatus(PENDING);
        invite.setExpiresAt(exp);
        roomInviteMapper.insert(invite);
        log.info(
                "[social-sse] createRoomInvite ok inviteId={} inviterUserId={} inviteeUserId={} roomId={} -> push SSE",
                invite.getId(),
                inviterUserId,
                inviteeUserId,
                invite.getRoomId());
        pushSocialNotify(inviteeUserId);
        ResultUtil ok = ResultUtil.ok();
        ok.data("inviteId", invite.getId());
        ok.data("roomId", invite.getRoomId());
        ok.data("expiresAt", invite.getExpiresAt().toString());
        return ok;
    }

    public ResultUtil mailbox(int currentUserId) {
        touchExpireRoomInvites();
        List<Map<String, Object>> friendRequests = new ArrayList<>();
        for (DpFriendRequestRow r : friendRequestMapper.listPendingFriendRequestsToUser(currentUserId)) {
            friendRequests.add(toFriendRequestPayload(r));
        }
        List<Map<String, Object>> roomInvites = new ArrayList<>();
        for (DpRoomInviteRow ri : roomInviteMapper.listPendingRoomInvitesToUser(currentUserId)) {
            roomInvites.add(toRoomInvitePayload(ri));
        }
        return ResultUtil.ok()
                .data("friendRequests", friendRequests)
                .data("roomInvites", roomInvites);
    }

    public ResultUtil unreadCount(int currentUserId) {
        touchExpireRoomInvites();
        int c1 = countPendingFriendRequestsToUser(currentUserId);
        int c2 = countPendingRoomInvitesToUserFresh(currentUserId);
        return ResultUtil.ok().data("count", c1 + c2);
    }

    /**
     * 大厅好友列表「跟随」：互为好友且好友在某房内时，与接受进房邀请相同，令当前用户以观众入会（免密）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultUtil followFriendToTheirRoom(int currentUserId, String currentNickname, int friendUserId) {
        if (friendUserId <= 0 || friendUserId == currentUserId) {
            return ResultUtil.error().data("message", "参数无效");
        }
        if (currentNickname == null || currentNickname.isBlank()) {
            return ResultUtil.error().data("message", "用户昵称无效");
        }
        String cn = currentNickname.trim();
        int lo = Math.min(currentUserId, friendUserId);
        int hi = Math.max(currentUserId, friendUserId);
        if (countFriendLinksBetween(lo, hi) == 0) {
            return ResultUtil.error().data("message", "已非好友，无法跟随进房");
        }
        DpUser friend = dpUserMapper.selectById(friendUserId);
        if (friend == null || friend.getNickname() == null || friend.getNickname().isBlank()) {
            return ResultUtil.error().data("message", "好友不存在");
        }
        String friendNick = friend.getNickname().trim();
        String roomId = dpRoomService.findRoomIdContainingNickname(friendNick);
        if (roomId == null || roomId.isBlank()) {
            return ResultUtil.error().data("message", "好友不在房间内");
        }
        if (!dpRoomService.dpRoomExistsInMemory(roomId)) {
            return ResultUtil.error().data("message", "房间已不存在").data("roomId", roomId);
        }
        String join = dpRoomService.joinRoomInviteAsSpectator(roomId, cn, currentUserId);
        if (!"ok".equals(join) && !"游戏已开始".equals(join)) {
            return ResultUtil.error().data("message", join).data("roomId", roomId);
        }
        return ResultUtil.ok()
                .data("message", "已跟随进房")
                .data("roomId", roomId)
                .data("observeNote", join);
    }

    public ResultUtil acceptRoomInvite(int currentUserId, String currentNickname, long inviteId) {
        touchExpireRoomInvites();
        DpRoomInviteRow inv = roomInviteMapper.selectById(inviteId);
        if (inv == null) {
            return ResultUtil.error().data("message", "邀请不存在");
        }
        if (inv.getInviteeUserId() != currentUserId) {
            return ResultUtil.error().data("message", "无权处理该邀请");
        }
        LocalDateTime now = LocalDateTime.now();
        if (ACCEPTED.equals(inv.getStatus())) {
            return ResultUtil.ok().data("message", "已同意过了").data("roomId", inv.getRoomId());
        }
        if (!PENDING.equals(inv.getStatus())) {
            return ResultUtil.ok().data("message", "邀请已失效").data("roomId", inv.getRoomId());
        }
        if (!inv.getExpiresAt().isAfter(now)) {
            updateRoomInviteStatus(inviteId, PENDING, EXPIRED);
            return ResultUtil.error().data("message", "邀请已过期").data("roomId", inv.getRoomId());
        }
        if (!dpRoomService.dpRoomExistsInMemory(inv.getRoomId())) {
            updateRoomInviteStatus(inviteId, PENDING, EXPIRED);
            return ResultUtil.error().data("message", "房间已不存在").data("roomId", inv.getRoomId());
        }

        int invLow = Math.min(inv.getInviterUserId(), inv.getInviteeUserId());
        int invHigh = Math.max(inv.getInviterUserId(), inv.getInviteeUserId());
        if (countFriendLinksBetween(invLow, invHigh) == 0) {
            updateRoomInviteStatus(inviteId, PENDING, CANCELLED);
            return ResultUtil.error().data("message", "已非好友，无法接受进房邀请").data("roomId", inv.getRoomId());
        }

        String join = dpRoomService.joinRoomInviteAsSpectator(inv.getRoomId(), currentNickname, currentUserId);
        if (!"ok".equals(join) && !"游戏已开始".equals(join)) {
            return ResultUtil.error().data("message", join).data("roomId", inv.getRoomId());
        }
        int n = updateRoomInviteStatus(inviteId, PENDING, ACCEPTED);
        if (n == 0) {
            DpRoomInviteRow again = roomInviteMapper.selectById(inviteId);
            if (again != null && ACCEPTED.equals(again.getStatus())) {
                return ResultUtil.ok().data("message", "已同意并进房").data("roomId", inv.getRoomId());
            }
            return ResultUtil.error().data("message", "同意失败").data("roomId", inv.getRoomId());
        }
        pushSocialNotify(currentUserId);
        return ResultUtil.ok().data("message", "已同意并进房").data("roomId", inv.getRoomId()).data("observeNote", join);
    }

    public ResultUtil rejectRoomInvite(int currentUserId, long inviteId) {
        touchExpireRoomInvites();
        DpRoomInviteRow inv = roomInviteMapper.selectById(inviteId);
        if (inv == null) {
            return ResultUtil.error().data("message", "邀请不存在");
        }
        if (inv.getInviteeUserId() != currentUserId) {
            return ResultUtil.error().data("message", "无权处理该邀请");
        }
        if (REJECTED.equals(inv.getStatus())) {
            return ResultUtil.ok().data("message", "已拒绝过了");
        }
        if (!PENDING.equals(inv.getStatus())) {
            return ResultUtil.ok().data("message", "邀请已不可用");
        }
        int u = updateRoomInviteStatus(inviteId, PENDING, REJECTED);
        if (u == 0) {
            inv = roomInviteMapper.selectById(inviteId);
            if (inv != null && REJECTED.equals(inv.getStatus())) {
                return ResultUtil.ok().data("message", "已拒绝过了");
            }
            return ResultUtil.ok().data("message", "邀请状态已变更");
        }
        pushSocialNotify(currentUserId);
        return ResultUtil.ok().data("message", "已拒绝");
    }

    /**
     * 只读：互为好友且对方在房内且具备与「发起进房邀请」相同的成员身份时返回 {@code roomId}
     * （字段与 {@link #createRoomInvite} 所需 {@code roomId} 一致）。
     * <p>数据来源：内存房间 {@link DpRoomService#findActiveRoomIdForNickname}
     * + {@link DpRoomService#canActiveMemberInviteFriends}；非 DB。</p>
     */
    public ResultUtil getFriendSpectateRoomContext(int viewerUserId, int friendUserId) {
        return resolveFriendSpectateRoomTarget(viewerUserId, friendUserId);
    }

    /**
     * 跟随好友以观众进房：校验通过后仅调用 {@link DpRoomService#joinRoomInviteAsSpectator}，
     * 与 {@link #acceptRoomInvite} 共用同一进房实现。
     */
    public ResultUtil followFriendIntoRoomAsSpectator(int viewerUserId, String viewerNickname, int friendUserId) {
        ResultUtil target = resolveFriendSpectateRoomTarget(viewerUserId, friendUserId);
        if (!Boolean.TRUE.equals(target.getSuccess())) {
            return target;
        }
        Map<String, Object> d = target.getData();
        String roomId = d != null && d.get("roomId") != null ? String.valueOf(d.get("roomId")) : "";
        String join = dpRoomService.joinRoomInviteAsSpectator(roomId, viewerNickname, viewerUserId);
        if (!"ok".equals(join) && !"游戏已开始".equals(join)) {
            return ResultUtil.error().data("message", join).data("roomId", roomId);
        }
        return ResultUtil.ok().data("message", "已跟随并进房").data("roomId", roomId).data("observeNote", join);
    }

    /**
     * @return success 时 {@code data.roomId} 非空；失败文案与邀请链路可对齐的尽量对齐（房间缺失、{@code joinRoomInviteAsSpectator} 返回值等）。
     */
    private ResultUtil resolveFriendSpectateRoomTarget(int viewerUserId, int friendUserId) {
        if (viewerUserId <= 0 || friendUserId <= 0) {
            return ResultUtil.error().data("message", "用户 id 无效");
        }
        if (viewerUserId == friendUserId) {
            return ResultUtil.error().data("message", "不能邀请自己");
        }
        int lo = Math.min(viewerUserId, friendUserId);
        int hi = Math.max(viewerUserId, friendUserId);
        if (countFriendLinksBetween(lo, hi) == 0) {
            return ResultUtil.error().data("message", "仅互为好友可邀请");
        }
        DpUser friend = dpUserMapper.selectById(friendUserId);
        if (friend == null) {
            return ResultUtil.error().data("message", "被邀请用户不存在");
        }
        String fn = friend.getNickname();
        if (fn == null || fn.isBlank()) {
            return ResultUtil.error().data("message", "用户不存在或未同步");
        }
        String roomId = dpRoomService.findActiveRoomIdForNickname(fn.trim());
        if (roomId == null || roomId.isBlank()) {
            return ResultUtil.error().data("message", "好友不在房间内");
        }
        if (!dpRoomService.dpRoomExistsInMemory(roomId)) {
            return ResultUtil.error().data("message", "房间已不存在").data("roomId", roomId);
        }
        if (!dpRoomService.canActiveMemberInviteFriends(roomId, fn.trim())) {
            return ResultUtil.error().data("message", "仅局内未离座成员或观众可发起进房邀请").data("roomId", roomId);
        }
        return ResultUtil.ok().data("roomId", roomId);
    }

    private DpFriendRequestRow selectFriendRequestByPair(int fromUserId, int toUserId) {
        return friendRequestMapper.selectOne(
                new LambdaQueryWrapper<DpFriendRequestRow>()
                        .eq(DpFriendRequestRow::getFromUserId, fromUserId)
                        .eq(DpFriendRequestRow::getToUserId, toUserId));
    }

    private void insertFriendRequest(int fromUserId, int toUserId) {
        DpFriendRequestRow row = new DpFriendRequestRow();
        row.setFromUserId(fromUserId);
        row.setToUserId(toUserId);
        row.setStatus(PENDING);
        row.setCreatedAt(LocalDateTime.now());
        friendRequestMapper.insert(row);
    }

    /**
     * 无序用户对：与列表查询「任一侧命中即命中」一致，避免历史脏数据把 (max,min) 写进两列时
     * 列表有、校验无 的分裂。
     */
    private int countFriendLinksBetween(int userA, int userB) {
        if (userA <= 0 || userB <= 0 || userA == userB) {
            return 0;
        }
        int lo = Math.min(userA, userB);
        int hi = Math.max(userA, userB);
        return Math.toIntExact(
                friendLinkMapper.selectCount(
                        new LambdaQueryWrapper<DpFriendLinkRow>()
                                .apply(
                                        "(user_low_id = {0} AND user_high_id = {1}) OR (user_low_id = {1} AND user_high_id = {0})",
                                        lo,
                                        hi)));
    }

    private void cancelPendingRoomInvitesBetweenUsers(int userA, int userB) {
        roomInviteMapper.update(
                null,
                new LambdaUpdateWrapper<DpRoomInviteRow>()
                        .set(DpRoomInviteRow::getStatus, CANCELLED)
                        .eq(DpRoomInviteRow::getStatus, PENDING)
                        .and(
                                w ->
                                        w.eq(DpRoomInviteRow::getInviterUserId, userA)
                                                .eq(DpRoomInviteRow::getInviteeUserId, userB)
                                                .or()
                                                .eq(DpRoomInviteRow::getInviterUserId, userB)
                                                .eq(DpRoomInviteRow::getInviteeUserId, userA)));
    }

    /** 双向将 ACCEPTED 好友申请标为 EXPIRED（删好友或幂等删 link 时兜底，不推 SSE）。 */
    private void expireAcceptedFriendRequestsBetweenUsers(int userA, int userB) {
        friendRequestMapper.update(
                null,
                new LambdaUpdateWrapper<DpFriendRequestRow>()
                        .set(DpFriendRequestRow::getStatus, EXPIRED)
                        .eq(DpFriendRequestRow::getStatus, ACCEPTED)
                        .and(
                                w ->
                                        w.eq(DpFriendRequestRow::getFromUserId, userA)
                                                .eq(DpFriendRequestRow::getToUserId, userB)
                                                .or()
                                                .eq(DpFriendRequestRow::getFromUserId, userB)
                                                .eq(DpFriendRequestRow::getToUserId, userA)));
    }

    /** 始终写入 user_low_id &lt; user_high_id，与表注释一致。 */
    private void insertFriendLink(int userA, int userB) {
        int lo = Math.min(userA, userB);
        int hi = Math.max(userA, userB);
        if (lo == hi) {
            throw new IllegalArgumentException("friend link requires two distinct users");
        }
        DpFriendLinkRow row = new DpFriendLinkRow();
        row.setUserLowId(lo);
        row.setUserHighId(hi);
        row.setCreatedAt(LocalDateTime.now());
        friendLinkMapper.insert(row);
    }

    private int updateFriendRequestStatus(long id, String expectStatus, String newStatus) {
        return friendRequestMapper.update(
                null,
                new LambdaUpdateWrapper<DpFriendRequestRow>()
                        .set(DpFriendRequestRow::getStatus, newStatus)
                        .eq(DpFriendRequestRow::getId, id)
                        .eq(DpFriendRequestRow::getStatus, expectStatus));
    }
/**
 * 查询当前用户有多少未处理的加好友请求
 * @param toUserId
 * @return
 */
    private int countPendingFriendRequestsToUser(int toUserId) {
        return Math.toIntExact(
                friendRequestMapper.selectCount(
                        new LambdaQueryWrapper<DpFriendRequestRow>()
                                .eq(DpFriendRequestRow::getToUserId, toUserId)
                                .eq(DpFriendRequestRow::getStatus, PENDING)));
    }
/**
 * 查询当前用户有多少未处理的进房邀请
 * @param inviteeUserId
 * @return
 */
    private int countPendingRoomInvitesToUserFresh(int inviteeUserId) {
        return Math.toIntExact(
                roomInviteMapper.selectCount(
                        new LambdaQueryWrapper<DpRoomInviteRow>()
                                .eq(DpRoomInviteRow::getInviteeUserId, inviteeUserId)
                                .eq(DpRoomInviteRow::getStatus, PENDING)
                                .apply("expires_at > NOW(3)")));
    }

    private int updateRoomInviteStatus(long id, String expectStatus, String newStatus) {
        return roomInviteMapper.update(
                null,
                new LambdaUpdateWrapper<DpRoomInviteRow>()
                        .set(DpRoomInviteRow::getStatus, newStatus)
                        .eq(DpRoomInviteRow::getId, id)
                        .eq(DpRoomInviteRow::getStatus, expectStatus));
    }

    private static Map<String, Object> toFriendRequestPayload(DpFriendRequestRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("fromUserId", r.getFromUserId());
        m.put("toUserId", r.getToUserId());
        m.put("fromNickname", r.getFromNickname());
        m.put("status", r.getStatus());
        if (r.getCreatedAt() != null) {
            m.put("createdAt", r.getCreatedAt().toString());
        }
        m.put("kind", "FRIEND_REQUEST");
        return m;
    }

    private static Map<String, Object> toRoomInvitePayload(DpRoomInviteRow ri) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ri.getId());
        m.put("roomId", ri.getRoomId());
        m.put("inviterUserId", ri.getInviterUserId());
        m.put("inviterNickname", ri.getInviterNickname());
        m.put("inviteeUserId", ri.getInviteeUserId());
        m.put("status", ri.getStatus());
        if (ri.getCreatedAt() != null) {
            m.put("createdAt", ri.getCreatedAt().toString());
        }
        if (ri.getExpiresAt() != null) {
            m.put("expiresAt", ri.getExpiresAt().toString());
            long sec = ChronoUnit.SECONDS.between(LocalDateTime.now(), ri.getExpiresAt());
            m.put("remainingSeconds", Math.max(sec, 0L));
        }
        m.put("kind", "ROOM_INVITE");
        return m;
    }
}
