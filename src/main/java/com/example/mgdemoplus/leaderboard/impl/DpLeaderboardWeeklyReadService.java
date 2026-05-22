package com.example.mgdemoplus.leaderboard.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.leaderboard.DpLeaderboardRedisRepository;
import com.example.mgdemoplus.leaderboard.DpLeaderboardWeekUtil;
import com.example.mgdemoplus.leaderboard.LeaderboardCompetitionRankUtil;
import com.example.mgdemoplus.leaderboard.entity.DpLeaderboardWeekly;
import com.example.mgdemoplus.leaderboard.mapper.DpLeaderboardWeeklyMapper;
import com.example.mgdemoplus.leaderboard.vo.WeeklyLeaderboardItemVO;
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

    public static final String STALE_HINT = "榜单约每分钟更新";

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
        return buildBoard("hand", DpLeaderboardWeekUtil.handRedisKey(currentWeek()), limit, viewerUserId, true);
    }

    public Map<String, Object> getRoomBoard(int limit, Integer viewerUserId) {
        return buildBoard("room", DpLeaderboardWeekUtil.roomRedisKey(currentWeek()), limit, viewerUserId, false);
    }

    private LocalDate currentWeek() {
        return DpLeaderboardWeekUtil.weekStartMonday(appZone);
    }

    private Map<String, Object> buildBoard(String board,
                                           String redisKey,
                                           int limit,
                                           Integer viewerUserId,
                                           boolean handBoard) {
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
            attachMyStats(data, weekMonday, viewerUserId, handBoard, redisKey);
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
        attachMyStats(data, weekMonday, viewerUserId, handBoard, redisKey);
        return data;
    }

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
            }
        }
    }

    private void attachMyStats(Map<String, Object> data,
                               LocalDate weekMonday,
                               Integer viewerUserId,
                               boolean handBoard,
                               String redisKey) {
        if (viewerUserId == null || viewerUserId <= 0) {
            return;
        }
        Long revRank = stringRedisTemplate.opsForZSet().reverseRank(redisKey, String.valueOf(viewerUserId));
        if (revRank != null) {
            data.put("myRank", revRank.intValue() + 1);
        } else {
            data.put("myRank", null);
        }

        DpLeaderboardWeekly row = dpLeaderboardWeeklyMapper.selectByWeekAndUser(weekMonday, viewerUserId);
        BigDecimal myMultiplier = null;
        if (row != null) {
            myMultiplier = handBoard ? row.getBestHandMultiplier() : row.getBestRoomMultiplier();
            if (myMultiplier != null && myMultiplier.signum() <= 0) {
                myMultiplier = null;
            }
        }
        data.put("myMultiplier", myMultiplier);
    }
}
