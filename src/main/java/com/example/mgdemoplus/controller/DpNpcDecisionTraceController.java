package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.npc.trace.DpNpcDecisionTraceQueryService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/dpRoom/npcDecisionTrace")
public class DpNpcDecisionTraceController {

    @Autowired
    private DpNpcDecisionTraceQueryService npcDecisionTraceQueryService;
    @Autowired
    private DpCurrentUserSupport currentUserSupport;

    @GetMapping("/hands")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GAME_NPC_DECISION_TRACE)")
    public ResultUtil listHands(
            @RequestParam String roomId,
            @RequestParam(required = false) String experimentalPassword) {
        DpUser me = requireUser();
        return npcDecisionTraceQueryService.listHands(roomId, me.getNickname(), experimentalPassword);
    }

    @GetMapping("/hand")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GAME_NPC_DECISION_TRACE)")
    public ResultUtil getHand(
            @RequestParam String roomId,
            @RequestParam long handSeed,
            @RequestParam(required = false) String experimentalPassword) {
        DpUser me = requireUser();
        return npcDecisionTraceQueryService.getHand(roomId, handSeed, me.getNickname(), experimentalPassword);
    }

    @GetMapping("/action")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GAME_NPC_DECISION_TRACE)")
    public ResultUtil getAction(
            @RequestParam String roomId,
            @RequestParam long handSeed,
            @RequestParam String actionId,
            @RequestParam(required = false) String experimentalPassword) {
        DpUser me = requireUser();
        return npcDecisionTraceQueryService.getAction(roomId, handSeed, actionId, me.getNickname(), experimentalPassword);
    }

    private DpUser requireUser() {
        DpUser me = currentUserSupport.requireUser();
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已失效");
        }
        return me;
    }
}
