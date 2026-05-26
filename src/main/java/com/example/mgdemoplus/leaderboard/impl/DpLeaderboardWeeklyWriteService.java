package com.example.mgdemoplus.leaderboard.impl;

import com.example.mgdemoplus.leaderboard.DpLeaderboardWeekUtil;
import com.example.mgdemoplus.leaderboard.mapper.DpLeaderboardWeeklyMapper;
import java.math.BigDecimal;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 周榜 MySQL 写旁路（策略 A：不写 Redis）。
 */
@Service
public class DpLeaderboardWeeklyWriteService {

    private static final Logger log = LoggerFactory.getLogger(DpLeaderboardWeeklyWriteService.class);

    private final DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper;
    private final ZoneId appZone;

    public DpLeaderboardWeeklyWriteService(
            DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper,
            @Value("${mgdemoplus.time-zone:Asia/Shanghai}") String timeZoneId) {
        this.dpLeaderboardWeeklyMapper = dpLeaderboardWeeklyMapper;
        this.appZone = ZoneId.of(timeZoneId);
    }

    public void recordHandBest(int userId, BigDecimal handMultiplier) {
        if (userId <= 0 || handMultiplier == null || handMultiplier.signum() <= 0) {
            return;
        }
        try {
            dpLeaderboardWeeklyMapper.upsertBestHand(
                    DpLeaderboardWeekUtil.weekStartMonday(appZone), userId, handMultiplier);
        } catch (Exception e) {
            log.warn("leaderboard weekly upsert hand failed userId={}: {}", userId, e.toString());
        }
    }

    public void recordRoomBest(int userId, BigDecimal roomMultiplier) {
        if (userId <= 0 || roomMultiplier == null || roomMultiplier.signum() <= 0) {
            return;
        }
        try {
            dpLeaderboardWeeklyMapper.upsertBestRoom(
                    DpLeaderboardWeekUtil.weekStartMonday(appZone), userId, roomMultiplier);
        } catch (Exception e) {
            log.warn("leaderboard weekly upsert room failed userId={}: {}", userId, e.toString());
        }
    }
}
