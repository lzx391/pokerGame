package com.example.mgdemoplus.controller.dp;

import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.example.mgdemoplus.service.serviceImpl.dp.DpFriendSocialService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 好友双向申请 / 局内成员进房邀请（邮箱 MVP）。全部需 JWT；当前用户仅从 token 昵称解析 dp_user.id，
 * 与请求体 {@code toUserId} / {@code inviteeUserId} 分离以避免越权。
 */
@RestController
@RequestMapping("/dp")
public class DpFriendMailboxController {

    @Autowired
    private DpFriendSocialService dpFriendSocialService;
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

    @GetMapping("/friends")
    public ResultUtil friends() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        return dpFriendSocialService.listFriends(me.getId());
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
        return dpFriendSocialService.createRoomInvite(me.getId(), me.getNickname(), roomId, inviteeUserId);
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
}
