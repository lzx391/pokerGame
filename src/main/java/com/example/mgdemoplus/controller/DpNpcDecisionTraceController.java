package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.npc.trace.DpNpcDecisionTraceQueryService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public ResultUtil listHands(
            @RequestParam String roomId,
            @RequestParam String experimentalPassword) {
        DpUser me = requireUser();
        return npcDecisionTraceQueryService.listHands(roomId, me.getNickname(), experimentalPassword);
    }

    @GetMapping("/hand")
    public ResultUtil getHand(
            @RequestParam String roomId,
            @RequestParam long handSeed,
            @RequestParam String experimentalPassword) {
        DpUser me = requireUser();
        return npcDecisionTraceQueryService.getHand(roomId, handSeed, me.getNickname(), experimentalPassword);
    }

    @GetMapping("/action")
    public ResultUtil getAction(
            @RequestParam String roomId,
            @RequestParam long handSeed,
            @RequestParam String actionId,
            @RequestParam String experimentalPassword) {
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
