package com.example.mgdemoplus.social.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mgdemoplus.social.entity.DpFriendLinkRow;
import com.example.mgdemoplus.social.entity.DpFriendMessageRow;
import com.example.mgdemoplus.social.mapper.DpFriendChatReadMapper;
import com.example.mgdemoplus.social.mapper.DpFriendLinkMapper;
import com.example.mgdemoplus.social.mapper.DpFriendMessageMapper;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.social.notify.SocialNotifyPublisher;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DpFriendChatService {

    private static final int MAX_BODY_LEN = 500;
    private static final int MAX_MESSAGES_PER_PAIR = 500;
    private static final long MIN_SEND_INTERVAL_MS = 1_200L;
    private static final int DEFAULT_PAGE_LIMIT = 50;
    private static final int MAX_PAGE_LIMIT = 100;

    private final DpFriendMessageMapper friendMessageMapper;
    private final DpFriendChatReadMapper friendChatReadMapper;
    private final DpFriendLinkMapper friendLinkMapper;
    private final DpUserMapper dpUserMapper;

    private final ConcurrentHashMap<Long, Long> lastSendMsBySender = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    private SocialNotifyPublisher socialNotifyPublisher;

    public DpFriendChatService(
            DpFriendMessageMapper friendMessageMapper,
            DpFriendChatReadMapper friendChatReadMapper,
            DpFriendLinkMapper friendLinkMapper,
            DpUserMapper dpUserMapper) {
        this.friendMessageMapper = friendMessageMapper;
        this.friendChatReadMapper = friendChatReadMapper;
        this.friendLinkMapper = friendLinkMapper;
        this.dpUserMapper = dpUserMapper;
    }
/**
 * 发送私信，返回更新后信息，推送通知
 */
    @Transactional
    public ResultUtil sendMessage(int senderUserId, int peerUserId, String bodyRaw) {
        if (peerUserId <= 0 || senderUserId <= 0) {
            return ResultUtil.error().data("message", "用户 id 无效");
        }
        if (senderUserId == peerUserId) {
            return ResultUtil.error().data("message", "不能给自己发私信");
        }
        if (!areFriends(senderUserId, peerUserId)) {
            return ResultUtil.error().data("message", "非好友，无法发送私信");
        }
        DpUser peer = dpUserMapper.selectById(peerUserId);
        if (peer == null) {
            return ResultUtil.error().data("message", "对方用户不存在");
        }
        String body = normalizeBody(bodyRaw);
        if (body.isEmpty()) {
            return ResultUtil.error().data("message", "消息不能为空");
        }
        if (body.length() > MAX_BODY_LEN) {
            return ResultUtil.error().data("message", "消息过长（最多 " + MAX_BODY_LEN + " 字）");
        }
        long now = System.currentTimeMillis();
        Long last = lastSendMsBySender.get((long) senderUserId);
        if (last != null && now - last < MIN_SEND_INTERVAL_MS) {
            return ResultUtil.error().data("message", "发送过于频繁，请稍后再试");
        }

        DpFriendMessageRow row = new DpFriendMessageRow();
        row.setSenderUserId(senderUserId);
        row.setRecipientUserId(peerUserId);
        row.setBody(body);
        row.setCreatedAt(LocalDateTime.now());
        friendMessageMapper.insert(row);
        lastSendMsBySender.put((long) senderUserId, now);

        trimConversationIfNeeded(senderUserId, peerUserId);

        socialNotifyPublisher.notifyUser(peerUserId);

        return ResultUtil.ok()
                .data("messageId", row.getId())
                .data("createdAt", row.getCreatedAt());
    }

    public ResultUtil listMessages(int currentUserId, int peerUserId, Long beforeId, Integer limit) {
        if (peerUserId <= 0) {
            return ResultUtil.error().data("message", "用户 id 无效");
        }
        if (!areFriends(currentUserId, peerUserId)) {
            return ResultUtil.error().data("message", "非好友，无法查看私信");
        }
        int lim = limit == null || limit <= 0 ? DEFAULT_PAGE_LIMIT : Math.min(limit, MAX_PAGE_LIMIT);
        List<DpFriendMessageRow> rows =
                friendMessageMapper.listConversation(currentUserId, peerUserId, beforeId, lim);
        Collections.reverse(rows);
        List<Map<String, Object>> items = new ArrayList<>();
        for (DpFriendMessageRow r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("messageId", r.getId());
            m.put("senderUserId", r.getSenderUserId());
            m.put("recipientUserId", r.getRecipientUserId());
            m.put("body", r.getBody());
            m.put("createdAt", r.getCreatedAt());
            m.put("mine", currentUserId == r.getSenderUserId());
            items.add(m);
        }
        return ResultUtil.ok().data("items", items);
    }

    @Transactional
    public ResultUtil markRead(int currentUserId, int peerUserId, long lastReadMessageId) {
        if (peerUserId <= 0 || lastReadMessageId < 0) {
            return ResultUtil.error().data("message", "参数无效");
        }
        if (!areFriends(currentUserId, peerUserId)) {
            return ResultUtil.error().data("message", "非好友");
        }
        friendChatReadMapper.upsertLastRead(currentUserId, peerUserId, lastReadMessageId);
        socialNotifyPublisher.notifyUser(currentUserId);
        return ResultUtil.ok().data("message", "ok");
    }

    /** 按好友 userId（字符串 key）聚合未读，供 SSE / REST 摘要。 */
    public Map<String, Integer> friendChatUnreadByFriendUserId(int currentUserId) {
        List<DpFriendLinkRow> friends = friendLinkMapper.listFriendsOfUser(currentUserId);
        Map<String, Integer> out = new LinkedHashMap<>();
        //遍历每一个friend
        for (DpFriendLinkRow fl : friends) {
            Integer fid = fl.getFriendUserId();
            if (fid == null || fid <= 0) {
                continue;
            }
            //查询当前用户有多少未读消息给好友
            long cnt = friendMessageMapper.countUnreadFromPeer(currentUserId, fid);
            if (cnt > 0) {
                out.put(String.valueOf(fid), (int) Math.min(cnt, Integer.MAX_VALUE));
            }
        }
        return out;
    }

    public ResultUtil unreadSummary(int currentUserId) {
        List<DpFriendLinkRow> friends = friendLinkMapper.listFriendsOfUser(currentUserId);
        List<Map<String, Object>> perFriend = new ArrayList<>();
        long total = 0;
        for (DpFriendLinkRow fl : friends) {
            Integer fid = fl.getFriendUserId();
            if (fid == null || fid <= 0) {
                continue;
            }
            long cnt = friendMessageMapper.countUnreadFromPeer(currentUserId, fid);
            if (cnt > 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", fid);
                row.put("count", cnt);
                perFriend.add(row);
                total += cnt;
            }
        }
        return ResultUtil.ok().data("totalUnread", total).data("perFriend", perFriend);
    }
/**
 * 如果超过500条，则删除最早的
 * @param userA
 * @param userB
 */
    private void trimConversationIfNeeded(int userA, int userB) {
        long count = friendMessageMapper.countConversation(userA, userB);
        if (count <= MAX_MESSAGES_PER_PAIR) {
            return;
        }
        int deleteCount = (int) (count - MAX_MESSAGES_PER_PAIR);
        friendMessageMapper.deleteOldestInConversation(userA, userB, deleteCount);
    }

    private boolean areFriends(int userA, int userB) {
        if (userA <= 0 || userB <= 0 || userA == userB) {
            return false;
        }
        int lo = Math.min(userA, userB);
        int hi = Math.max(userA, userB);
        return friendLinkMapper.selectCount(
                        new LambdaQueryWrapper<DpFriendLinkRow>()
                                .apply(
                                        "(user_low_id = {0} AND user_high_id = {1}) OR (user_low_id = {1} AND user_high_id = {0})",
                                        lo,
                                        hi))
                > 0;
    }

    private static String normalizeBody(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
