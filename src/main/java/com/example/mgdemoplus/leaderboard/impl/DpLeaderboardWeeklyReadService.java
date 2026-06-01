package com.example.mgdemoplus.leaderboard.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.leaderboard.DpLeaderboardRedisRepository;
import com.example.mgdemoplus.leaderboard.DpLeaderboardWeekUtil;
import com.example.mgdemoplus.leaderboard.LeaderboardCompetitionRankUtil;
import com.example.mgdemoplus.leaderboard.entity.DpLeaderboardWeekly;
import com.example.mgdemoplus.leaderboard.mapper.DpLeaderboardWeeklyMapper;
import com.example.mgdemoplus.leaderboard.vo.DpWeeklyLeaderboardPlacementView;
import com.example.mgdemoplus.leaderboard.vo.WeeklyLeaderboardItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.mgdemoplus.utils.DpDateTimeSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
public class DpLeaderboardWeeklyReadService {

    private static final Logger log = LoggerFactory.getLogger(DpLeaderboardWeeklyReadService.class);

    public static final String STALE_HINT = "榜单约每分钟更新";
    public static final String BOARD_HAND = "hand";
    public static final String BOARD_ROOM = "room";

    private final StringRedisTemplate stringRedisTemplate;
    private final DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper;
    private final DpUserMapper dpUserMapper;
    private final ZoneId appZone;

    public DpLeaderboardWeeklyReadService(
            StringRedisTemplate stringRedisTemplate,
            DpLeaderboardWeeklyMapper dpLeaderboardWeeklyMapper,
            DpUserMapper dpUserMapper,
            @Value("${mgdemoplus.time-zone:Asia/Shanghai}") String timeZoneId) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dpLeaderboardWeeklyMapper = dpLeaderboardWeeklyMapper;
        this.dpUserMapper = dpUserMapper;
        this.appZone = ZoneId.of(timeZoneId);
    }

    public Map<String, Object> getHandBoard(int limit, Integer viewerUserId) {
        return buildBoard(BOARD_HAND, DpLeaderboardWeekUtil.handRedisKey(currentWeek()), limit, viewerUserId);
    }

    public Map<String, Object> getRoomBoard(int limit, Integer viewerUserId) {
        return buildBoard(BOARD_ROOM, DpLeaderboardWeekUtil.roomRedisKey(currentWeek()), limit, viewerUserId);
    }

    /** 指定用户在手牌周榜上的位次与倍数（Redis + MySQL，与榜单接口 myRank/myMultiplier 一致）。 */
    public DpWeeklyLeaderboardPlacementView placementForHand(int userId) {
        return lookupPlacement(BOARD_HAND, userId);
    }

    /** 指定用户在房间净赢周榜上的位次与倍数。 */
    public DpWeeklyLeaderboardPlacementView placementForRoom(int userId) {
        return lookupPlacement(BOARD_ROOM, userId);
    }

    public DpWeeklyLeaderboardPlacementView lookupPlacement(String board, int userId) {
        if (userId <= 0) {
            return null;
        }
        LocalDate weekMonday = currentWeek();
        boolean handBoard = BOARD_HAND.equals(board);
        String redisKey = handBoard
                ? DpLeaderboardWeekUtil.handRedisKey(weekMonday)
                : DpLeaderboardWeekUtil.roomRedisKey(weekMonday);

        DpWeeklyLeaderboardPlacementView placement = new DpWeeklyLeaderboardPlacementView();
        placement.setBoard(board);
        placement.setWeekMonday(weekMonday.toString());

        try {
            Long revRank = stringRedisTemplate.opsForZSet().reverseRank(redisKey, String.valueOf(userId));
            if (revRank != null) {
                placement.setRank(revRank.intValue() + 1);
            }
        } catch (Exception e) {
            log.warn("leaderboard redis rank lookup failed board={} userId={}: {}", board, userId, e.toString());
        }

        placement.setMultiplier(resolveMultiplierFromMysql(weekMonday, userId, handBoard));
        return placement;
    }

    private LocalDate currentWeek() {
        return DpLeaderboardWeekUtil.weekStartMonday(appZone);
    }

    private Map<String, Object> buildBoard(String board,
                                           String redisKey,
                                           int limit,
                                           Integer viewerUserId) {
        int capped = Math.min(Math.max(limit, 1), 50);
        LocalDate weekMonday = currentWeek();
        Map<String, Object> data = new HashMap<>();
        data.put("weekMonday", weekMonday.toString());
        data.put("board", board);
        data.put("staleHint", STALE_HINT);

        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, capped - 1);
        if (tuples == null || tuples.isEmpty()) {
            data.put("items", List.of());
            attachMyStats(data, board, viewerUserId);
            return data;
        }

        List<WeeklyLeaderboardItemVO> items = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            if (t.getValue() == null || t.getScore() == null) {
                continue;
            }
            WeeklyLeaderboardItemVO item = new WeeklyLeaderboardItemVO();
            item.setUserId(Integer.parseInt(t.getValue()));
            item.setMultiplier(DpLeaderboardRedisRepository.fromRedisScore(t.getScore()));
            items.add(item);
        }

        LeaderboardCompetitionRankUtil.applyCompetitionRanks(
                items, WeeklyLeaderboardItemVO::getMultiplier, WeeklyLeaderboardItemVO::setRank);
        enrichNicknames(items);
        data.put("items", items);

        attachMyStats(data, board, viewerUserId);
        return data;
    }
/**
 * 丰富资料卡的nickname、avatarUrl、avatarUpdatedAt
 * @param items
 */
    private void enrichNicknames(List<WeeklyLeaderboardItemVO> items) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (WeeklyLeaderboardItemVO item : items) {
            ids.add(item.getUserId());
        }
        Map<Integer, DpUser> users = new HashMap<>();
        for (int id : ids) {
            DpUser u = dpUserMapper.selectById(id);
            if (u != null) {
                users.put(id, u);
            }
        }
        for (WeeklyLeaderboardItemVO item : items) {
            DpUser u = users.get(item.getUserId());
            if (u != null) {
                item.setNickname(u.getNickname());
                item.setAvatarUrl(u.getAvatarUrl());
                item.setAvatarUpdatedAt(DpDateTimeSupport.toEpochMilli(u.getAvatarUpdatedAt()));
            }
        }
    }
/**
 * 我的数据
 * @param data
 * @param board
 * @param viewerUserId
 */
    private void attachMyStats(Map<String, Object> data, String board, Integer viewerUserId) {
        if (viewerUserId == null || viewerUserId <= 0) {
            return;
        }
        DpWeeklyLeaderboardPlacementView placement = lookupPlacement(board, viewerUserId);
        data.put("myRank", placement.getRank());
        data.put("myMultiplier", placement.getMultiplier());
    }

    private BigDecimal resolveMultiplierFromMysql(LocalDate weekMonday, int userId, boolean handBoard) {
        DpLeaderboardWeekly row = dpLeaderboardWeeklyMapper.selectByWeekAndUser(weekMonday, userId);
        if (row == null) {
            return null;
        }
        BigDecimal multiplier = handBoard ? row.getBestHandMultiplier() : row.getBestRoomMultiplier();
        if (multiplier != null && multiplier.signum() <= 0) {
            return null;
        }
        return multiplier;
    }
}
