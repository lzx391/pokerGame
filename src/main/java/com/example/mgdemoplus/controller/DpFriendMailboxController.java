package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.presence.DpSitePresenceService;
import com.example.mgdemoplus.social.impl.DpFriendChatService;
import com.example.mgdemoplus.social.impl.DpFriendSocialService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 好友双向申请 / 私信 / 局内成员进房邀请（邮箱 MVP）。全部需 JWT；当前用户仅从 token 昵称解析
 * {@code dp_user.id}，与请求体 {@code toUserId} / {@code inviteeUserId} 分离以避免越权。
 * <p>{@code GET /dp/friends}：分页好友列表（默认 page=1、pageSize=20，最大 100），可选 {@code q}
 * 筛选（纯数字→好友 userId；否则昵称包含、不区分大小写），按最近私信时间降序（无消息则按成为好友时间）。</p>
 * <p>{@code GET /dp/users/lookup?q=}：加好友前精确查人（数字→id，否则昵称全等），仅返回公开资料 +
 * {@code addStatus}（CAN_ADD / SELF / ALREADY_FRIENDS / PENDING_*）。</p>
 */
@RestController
@RequestMapping("/dp")
public class DpFriendMailboxController {

    private static final Logger log = LoggerFactory.getLogger(DpFriendMailboxController.class);

    @Autowired
    private DpFriendSocialService dpFriendSocialService;
    @Autowired
    private DpFriendChatService dpFriendChatService;
    @Autowired
    private DpSitePresenceService dpSitePresenceService;
    @Autowired
    private DpUserMapper dpUserMapper;

    private DpUser requireCurrentUser(ResultUtil fallback) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            fallback.setSuccess(false);
            fallback.setMessage("未登录或登录已失效");
            return null;
        }
        String nickname = auth.getName();
        DpUser u = dpUserMapper.selectByNickname(nickname);
        if (u == null) {
            fallback.setSuccess(false);
            fallback.setMessage("用户不存在或未同步");
            return null;
        }
        return u;
    }

    @PostMapping("/friends/requests")
    public ResultUtil sendFriendRequest(@RequestBody Map<String, Object> body) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        Object raw = body != null ? body.get("toUserId") : null;
        int toUserId = raw instanceof Number ? ((Number) raw).intValue() : -1;
        return dpFriendSocialService.sendFriendRequest(me.getId(), toUserId);
    }

    @GetMapping("/friends/requests/pending")
    public ResultUtil pendingFriendInbound() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.listPendingFriendRequestsInbound(me.getId());
    }

    @PostMapping("/friends/requests/{id}/accept")
    public ResultUtil acceptFriend(@PathVariable("id") long id) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.acceptFriendRequest(me.getId(), id);
    }

    @PostMapping("/friends/requests/{id}/reject")
    public ResultUtil rejectFriend(@PathVariable("id") long id) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.rejectFriendRequest(me.getId(), id);
    }

    /**
     * 好友列表（分页）。响应 {@code data.friends}、{@code data.total}、{@code data.page}、{@code data.pageSize}。
     */
    @GetMapping("/friends")
    public ResultUtil friends(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String q) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.listFriends(me.getId(), page, pageSize, q);
    }

    /**
     * 加好友精确查人：{@code q} 为纯数字且 &gt;0 时按 id；否则按昵称全等（与 {@link DpUserMapper#selectByNickname} 一致）。
     */
    @GetMapping("/users/lookup")
    public ResultUtil lookupUserForFriendAdd(@RequestParam("q") String q) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.lookupUserForFriendAdd(me.getId(), q);
    }

    /**
     * 匿名可读：站点心跳 TTL 与建议间隔（与 POST /dp/presence/site-heartbeat 一致）。
     */
    @GetMapping("/presence/site-heartbeat/config")
    public ResultUtil sitePresenceHeartbeatConfig() {
        return ResultUtil.ok()
                .data("ttlMs", dpSitePresenceService.getTtlMs())
                .data("suggestedIntervalMs", dpSitePresenceService.getSuggestedHeartbeatIntervalMs());
    }

    /**
     * 站点级在线心跳：刷新当前用户的 {@code last_seen_site}（与房内轮询心跳无关）。
     * 客户端建议间隔 &lt; TTL/3；默认 TTL 见 {@code mgdemoplus.dp-site-presence-ttl-ms}。
     */
    @PostMapping("/presence/site-heartbeat")
    public ResultUtil sitePresenceHeartbeat() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        dpSitePresenceService.touchSiteHeartbeat(me.getId());
        return ResultUtil.ok()
                .data("message", "ok")
                .data("ttlMs", dpSitePresenceService.getTtlMs())
                .data("suggestedIntervalMs", dpSitePresenceService.getSuggestedHeartbeatIntervalMs());
    }

    /**
     * 互为好友且对方在某房间内时，当前用户以观众身份进入其房间（与邮箱「接受进房邀请」同属
     * {@link com.example.mgdemoplus.room.DpRoomService#joinRoomInviteAsSpectator}）。
     */
    @PostMapping("/friends/follow-room")
    public ResultUtil followFriendToRoom(@RequestBody Map<String, Object> body) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        Object raw = body != null ? body.get("friendUserId") : null;
        int friendUserId = raw instanceof Number ? ((Number) raw).intValue() : -1;
        if (friendUserId <= 0) {
            return ResultUtil.error().data("message", "参数无效");
        }
        return dpFriendSocialService.followFriendToTheirRoom(me.getId(), me.getNickname(), friendUserId);
    }

    @PostMapping("/friends/{peerUserId}/messages")
    public ResultUtil sendFriendMessage(
            @PathVariable("peerUserId") int peerUserId, @RequestBody Map<String, Object> body) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        Object raw = body != null ? body.get("body") : null;
        String text = raw != null ? raw.toString() : "";
        return dpFriendChatService.sendMessage(me.getId(), peerUserId, text);
    }

    @GetMapping("/friends/{peerUserId}/messages")
    public ResultUtil listFriendMessages(
            @PathVariable("peerUserId") int peerUserId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendChatService.listMessages(me.getId(), peerUserId, beforeId, limit);
    }

    @PostMapping("/friends/{peerUserId}/messages/read")
    public ResultUtil markFriendMessagesRead(
            @PathVariable("peerUserId") int peerUserId, @RequestBody Map<String, Object> body) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        Object raw = body != null ? body.get("lastReadMessageId") : null;
        long lastId = raw instanceof Number ? ((Number) raw).longValue() : -1L;
        return dpFriendChatService.markRead(me.getId(), peerUserId, lastId);
    }

    @GetMapping("/friends/chat-unread-summary")
    public ResultUtil friendChatUnreadSummary() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendChatService.unreadSummary(me.getId());
    }

    @DeleteMapping("/friends/{friendUserId}")
    public ResultUtil removeFriend(@PathVariable("friendUserId") int friendUserId) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.removeFriend(me.getId(), friendUserId);
    }

    @PostMapping("/room-invites")
    public ResultUtil createRoomInvite(@RequestBody Map<String, Object> body) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        if (body == null) {
            return ResultUtil.error().data("message", "缺少请求体");
        }
        Object roomObj = body.get("roomId");
        String roomId = roomObj != null ? roomObj.toString().trim() : "";
        Object invObj = body.get("inviteeUserId");
        int inviteeUserId = invObj instanceof Number ? ((Number) invObj).intValue() : -1;
        log.info(
                "[social-sse] POST /dp/room-invites inviterUserId={} inviteeUserId={} roomId={}",
                me.getId(),
                inviteeUserId,
                roomId);
        ResultUtil result =
                dpFriendSocialService.createRoomInvite(me.getId(), me.getNickname(), roomId, inviteeUserId);
        log.info(
                "[social-sse] POST /dp/room-invites result success={} message={}",
                Boolean.TRUE.equals(result.getSuccess()),
                result.getMessage());
        return result;
    }

    @GetMapping("/mailbox")
    public ResultUtil mailbox() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.mailbox(me.getId());
    }

    @GetMapping("/mailbox/unread-count")
    public ResultUtil unreadMailbox() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.unreadCount(me.getId());
    }

    @PostMapping("/room-invites/{id}/accept")
    public ResultUtil acceptInvite(@PathVariable("id") long id) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.acceptRoomInvite(me.getId(), me.getNickname(), id);
    }

    @PostMapping("/room-invites/{id}/reject")
    public ResultUtil rejectInvite(@PathVariable("id") long id) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.rejectRoomInvite(me.getId(), id);
    }

    /**
     * 只读：解析好友当前可跟随观战的房间号（与邀请进房所需的 {@code roomId} 字段一致）。
     * 详见 {@link DpFriendSocialService#getFriendSpectateRoomContext}。
     */
    @GetMapping("/friends/{friendUserId}/spectate-room")
    public ResultUtil friendSpectateRoom(@PathVariable("friendUserId") int friendUserId) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.getFriendSpectateRoomContext(me.getId(), friendUserId);
    }

    /**
     * 跟随好友进房（观众）：内部仅调用 {@code joinRoomInviteAsSpectator}，与同意邮箱邀约共用实现。
     */
    @PostMapping("/room-follow/spectate")
    public ResultUtil followFriendSpectate(@RequestBody Map<String, Object> body) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        if (body == null) {
            return ResultUtil.error().data("message", "缺少请求体");
        }
        Object raw = body.get("friendUserId");
        int friendUserId = raw instanceof Number ? ((Number) raw).intValue() : -1;
        return dpFriendSocialService.followFriendIntoRoomAsSpectator(me.getId(), me.getNickname(), friendUserId);
    }
}
