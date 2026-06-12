package com.example.mgdemoplus.leaderboard.impl;

import com.example.mgdemoplus.leaderboard.DpLeaderboardWeekUtil;
import com.example.mgdemoplus.leaderboard.LeaderboardCompetitionRankUtil;
import com.example.mgdemoplus.leaderboard.entity.DpLeaderboardWeekly;
import com.example.mgdemoplus.leaderboard.mapper.DpLeaderboardWeeklyFinalizedMapper;
import com.example.mgdemoplus.leaderboard.mapper.DpLeaderboardWeeklyMapper;
import com.example.mgdemoplus.leaderboard.vo.WeeklyLeaderboardItemVO;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 周榜换周前结算：各榜竞赛排名前三（rank ≤ 3）玩家的 {@code leaderboard_top_count} +1。
 */
@Service
public class DpLeaderboardWeeklyFinalizeService {

    private static final Logger log = LoggerFactory.getLogger(DpLeaderboardWeeklyFinalizeService.class);

    private static final int TOP_RANK_CUTOFF = 3;

    private final DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper;
    private final DpLeaderboardWeeklyFinalizedMapper finalizedMapper;
    private final DpUserStatsMapper dpUserStatsMapper;
    private final ZoneId appZone;

    public DpLeaderboardWeeklyFinalizeService(
            DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper,
            DpLeaderboardWeeklyFinalizedMapper finalizedMapper,
            DpUserStatsMapper dpUserStatsMapper,
            @Value("${mgdemoplus.time-zone:Asia/Shanghai}") String timeZoneId) {
        this.dpLeaderboardWeeklyMapper = dpLeaderboardWeeklyMapper;
        this.finalizedMapper = finalizedMapper;
        this.dpUserStatsMapper = dpUserStatsMapper;
        this.appZone = ZoneId.of(timeZoneId);
    }

    /**
     * 在 Redis 全量刷入当前周之前调用：补算所有尚未标记的过往周（含刚结束的上一周）。
     */
    public void finalizePendingWeeksBeforeRefresh() {
        LocalDate currentWeek = DpLeaderboardWeekUtil.weekStartMonday(appZone);
        LocalDate lastFinalized = finalizedMapper.selectMaxFinalizedWeek();
        LocalDate weekToProcess = lastFinalized == null
                ? currentWeek.minusWeeks(1)
                : lastFinalized.plusWeeks(1);
        while (weekToProcess.isBefore(currentWeek)) {
            finalizeSingleWeek(weekToProcess);
            weekToProcess = weekToProcess.plusWeeks(1);
        }
    }

    private void finalizeSingleWeek(LocalDate weekMonday) {
        if (finalizedMapper.countByWeek(weekMonday) > 0) {
            return;
        }
        try {
            awardTopThreeForBoard(weekMonday, true);
            awardTopThreeForBoard(weekMonday, false);
            finalizedMapper.tryMarkFinalized(weekMonday);
            log.info("leaderboard week finalized weekMonday={}", weekMonday);
        } catch (Exception e) {
            log.warn("leaderboard week finalize failed weekMonday={}: {}", weekMonday, e.toString());
        }
    }

    private void awardTopThreeForBoard(LocalDate weekMonday, boolean handBoard) {
        List<DpLeaderboardWeekly> rows = dpLeaderboardWeeklyMapper.selectAllForWeek(weekMonday);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<WeeklyLeaderboardItemVO> items = new ArrayList<>();
        for (DpLeaderboardWeekly row : rows) {
            if (row == null || row.getUserId() <= 0) {
                continue;
            }
            BigDecimal multiplier = handBoard ? row.getBestHandMultiplier() : row.getBestRoomMultiplier();
            if (multiplier == null || multiplier.signum() <= 0) {
                continue;
            }
            WeeklyLeaderboardItemVO item = new WeeklyLeaderboardItemVO();
            item.setUserId(row.getUserId());
            item.setMultiplier(multiplier);
            items.add(item);
        }
        if (items.isEmpty()) {
            return;
        }
        items.sort(Comparator.comparing(WeeklyLeaderboardItemVO::getMultiplier).reversed());
        LeaderboardCompetitionRankUtil.applyCompetitionRanks(
                items, WeeklyLeaderboardItemVO::getMultiplier, WeeklyLeaderboardItemVO::setRank);

        Set<Integer> awarded = new LinkedHashSet<>();
        for (WeeklyLeaderboardItemVO item : items) {
            if (item.getRank() > TOP_RANK_CUTOFF) {
                break;
            }
            if (awarded.add(item.getUserId())) {
                dpUserStatsMapper.incrementLeaderboardTopCount(item.getUserId(), 1);
            }
        }
    }
}
