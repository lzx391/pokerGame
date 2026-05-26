package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.social.notify.SocialNotifyPayload;
import com.example.mgdemoplus.social.notify.SocialNotifySummaryService;
import com.example.mgdemoplus.social.notify.SocialSseHub;
import com.example.mgdemoplus.utils.ResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 这里只管大厅的，不涉及邮箱和好友消息box的细节
 * 大厅社交通知：SSE 实时推送 + REST 摘要（断线兜底）。
 */
@RestController
@RequestMapping("/dp/social")
public class DpSocialController {

    private static final Logger log = LoggerFactory.getLogger(DpSocialController.class);

    @Autowired
    private SocialNotifySummaryService socialNotifySummaryService;
    @Autowired
    private SocialSseHub socialSseHub;
    @Autowired
    private DpUserMapper dpUserMapper;
/**
 * 建立SSE连接并触发一次推送
 * @return
 */
    @GetMapping("/stream")
    public ResponseEntity<?> socialStream() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            log.warn("[social-sse] stream rejected: not authenticated ({})", err.getMessage());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", err.getCode());
            body.put("message", err.getMessage() != null ? err.getMessage() : "未登录或登录已失效");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }
        log.info("[social-sse] stream opening userId={} nickname={}", me.getId(), me.getNickname());
        SocialNotifyPayload initial = socialNotifySummaryService.buildForUser(me.getId());
        SseEmitter emitter = socialSseHub.connect(me.getId(), initial);
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }
/**
 * 轮询兜底，防止SSE断开推送不了
 * @return
 */
    @GetMapping("/notify-summary")
    public ResultUtil notifySummary() {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        SocialNotifyPayload p = socialNotifySummaryService.buildForUser(me.getId());
        return ResultUtil.ok().data(p.toDataMap());
    }
/**
 * 验证当前用户是否登录，获取用户名
 * @param fallback
 * @return
 */
    private DpUser requireCurrentUser(ResultUtil fallback) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
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
}
