package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyReadService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.utils.ResultUtil;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 周榜只读 Redis；permitAll，登录时附加 myRank / myMultiplier。
 */
@RestController
@RequestMapping("/dp/leaderboard/weekly")
public class DpLeaderboardController {

    @Autowired
    private DpLeaderboardWeeklyReadService dpLeaderboardWeeklyReadService;
    @Autowired
    private DpCurrentUserSupport currentUserSupport;

    @GetMapping("/hand")
    public ResultUtil weeklyHand(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> payload = dpLeaderboardWeeklyReadService.getHandBoard(limit, resolveOptionalUserId());
        return ResultUtil.ok().data(payload);
    }

    @GetMapping("/room")
    public ResultUtil weeklyRoom(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> payload = dpLeaderboardWeeklyReadService.getRoomBoard(limit, resolveOptionalUserId());
        return ResultUtil.ok().data(payload);
    }

    private Integer resolveOptionalUserId() {
        return currentUserSupport.resolveUserOptional().map(u -> u.getId()).orElse(null);
    }
}
