package com.example.mgdemoplus.leaderboard;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 策略 A：唯一写 Redis 入口，每 N 秒从 MySQL 全量刷入当前周 ZSET。
 */
@Component
@ConditionalOnProperty(name = "mgdemoplus.leaderboard.sync-enabled", havingValue = "true", matchIfMissing = true)
public class DpLeaderboardWeeklyRedisSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DpLeaderboardWeeklyRedisSyncScheduler.class);

    private final DpLeaderboardRedisRepository dpLeaderboardRedisRepository;
    private final ZoneId appZone;

    public DpLeaderboardWeeklyRedisSyncScheduler(
            DpLeaderboardRedisRepository dpLeaderboardRedisRepository,
            @Value("${mgdemoplus.time-zone:Asia/Shanghai}") String timeZoneId) {
        this.dpLeaderboardRedisRepository = dpLeaderboardRedisRepository;
        this.appZone = ZoneId.of(timeZoneId);
    }

    @Scheduled(
            fixedDelayString = "${mgdemoplus.leaderboard.sync-interval-ms:60000}",
            initialDelayString = "${mgdemoplus.leaderboard.sync-interval-ms:60000}")
    public void syncCurrentWeekToRedis() {
        try {
            LocalDate week = DpLeaderboardWeekUtil.weekStartMonday(appZone);
            dpLeaderboardRedisRepository.rebuildCurrentWeekFromMysql(week);
        } catch (Exception e) {
            log.warn("leaderboard redis sync scheduler failed: {}", e.toString());
        }
    }
}
