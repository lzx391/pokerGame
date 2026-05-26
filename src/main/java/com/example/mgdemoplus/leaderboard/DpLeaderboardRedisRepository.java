package com.example.mgdemoplus.leaderboard;

import com.example.mgdemoplus.leaderboard.entity.DpLeaderboardWeekly;
import com.example.mgdemoplus.leaderboard.mapper.DpLeaderboardWeeklyMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 周榜 Redis ZSET：仅由定时任务全量重建（策略 A）。
 */
@Repository
public class DpLeaderboardRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(DpLeaderboardRedisRepository.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper;
    private final long redisTtlSeconds;

    public DpLeaderboardRedisRepository(
            StringRedisTemplate stringRedisTemplate,
            DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper,
            @Value("${mgdemoplus.leaderboard.redis-ttl-days:14}") int redisTtlDays) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dpLeaderboardWeeklyMapper = dpLeaderboardWeeklyMapper;
        this.redisTtlSeconds = Math.max(1, redisTtlDays) * 86400L;
    }

    /** 从 MySQL 拉取当前周并重建 hand / room 两个 ZSET。 */
    public void rebuildCurrentWeekFromMysql(LocalDate weekMonday) {
        List<DpLeaderboardWeekly> rows = dpLeaderboardWeeklyMapper.selectAllForWeek(weekMonday);
        String handKey = DpLeaderboardWeekUtil.handRedisKey(weekMonday);
        String roomKey = DpLeaderboardWeekUtil.roomRedisKey(weekMonday);
        try {
            stringRedisTemplate.delete(handKey);
            stringRedisTemplate.delete(roomKey);
            for (DpLeaderboardWeekly row : rows) {
                if (row == null) {
                    continue;
                }
                String member = String.valueOf(row.getUserId());
                BigDecimal hand = row.getBestHandMultiplier();
                if (hand != null && hand.signum() > 0) {
                    stringRedisTemplate.opsForZSet().add(handKey, member, toRedisScore(hand));
                }
                BigDecimal room = row.getBestRoomMultiplier();
                if (room != null && room.signum() > 0) {
                    stringRedisTemplate.opsForZSet().add(roomKey, member, toRedisScore(room));
                }
            }
            stringRedisTemplate.expire(handKey, java.time.Duration.ofSeconds(redisTtlSeconds));
            stringRedisTemplate.expire(roomKey, java.time.Duration.ofSeconds(redisTtlSeconds));
        } catch (Exception e) {
            log.warn("leaderboard redis sync failed: {}", e.toString());
        }
    }

    public static double toRedisScore(BigDecimal multiplier) {
        return multiplier.movePointRight(2).longValue();
    }

    public static BigDecimal fromRedisScore(double score) {
        return BigDecimal.valueOf(score).movePointLeft(2);
    }
}
