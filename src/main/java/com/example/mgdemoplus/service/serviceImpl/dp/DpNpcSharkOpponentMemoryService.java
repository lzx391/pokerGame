package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.entity.dp.DpSharkOpponentProfile;
import com.example.mgdemoplus.entity.dp.DpPlayerStats;
import com.example.mgdemoplus.mapper.dp.DpSharkOpponentProfileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 Shark 参考的 {@link DpPlayerStats} 与 {@link DpNpcSharkLearningLab} 旋钮按对手昵称持久化，
 * 跨随机房间 ID 认出同一玩家并恢复习惯画像。
 */
@Service
public class DpNpcSharkOpponentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(DpNpcSharkOpponentMemoryService.class);
    private static final int PAYLOAD_VERSION = 1;
    /** 与结算处 {@code stats.addHand(hand, 10)} 一致 */
    private static final int STATS_HAND_WINDOW = 10;

    private final DpSharkOpponentProfileMapper mapper;
    private final ObjectMapper objectMapper;

    public DpNpcSharkOpponentMemoryService(
            DpSharkOpponentProfileMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 一手结算且 LearningLab 已更新后调用：为每位非 Shark 对手 upsert 一行。
     */
    public void persistOpponentsAfterHand(DpRoom room) {
        if (room == null || !DpHandHistoryObservedImpl.isSharkAtTable(room)) {
            return;
        }
        Map<String, DpPlayerStats> statsMap = room.getPlayerStatsMap();
        if (statsMap == null || statsMap.isEmpty()) {
            return;
        }
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return;
        }
        for (DpPlayer p : ps) {
            if (p == null) {
                continue;
            }
            String name = p.getNickname();
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (DpNpcEngine.SHARK_BOT_NICKNAME.equals(name)) {
                continue;
            }
            DpPlayerStats stats = statsMap.get(name);
            if (stats == null) {
                continue;
            }
            try {
                StatsPayload sp = new StatsPayload();
                sp.totalHands = stats.getTotalHands();
                sp.totalParticipated = stats.getTotalParticipatedCount();
                sp.totalRaised = stats.getTotalRaisedCount();
                sp.totalShowdown = stats.getTotalShowdownCount();
                sp.foldAdjustmentAgainstHero = stats.getFoldAdjustmentAgainstHero();
                sp.recentHands = new ArrayList<>(stats.getRecentHands());

                String statsJson = objectMapper.writeValueAsString(sp);
                DpNpcSharkLearningLab.LearnedPersistSnapshot learned = DpNpcSharkLearningLab
                        .exportLearnedSnapshot(name);
                String learnedJson = objectMapper.writeValueAsString(learned);

                DpSharkOpponentProfile row = new DpSharkOpponentProfile();
                row.setPlayerNickname(name);
                row.setStatsJson(statsJson);
                row.setLearnedJson(learnedJson);
                row.setPayloadVersion(PAYLOAD_VERSION);
                mapper.upsert(row);
            } catch (Exception e) {
                log.warn("dp_shark_opponent_profile upsert failed nickname={}: {}", name, e.getMessage());
            }
        }
    }

    /**
     * 若本桌有 Shark 且该昵称尚无内存统计，则从 DB 恢复（避免覆盖本局已累积的 map）。
     */
    public void hydratePlayerIfNeeded(DpRoom room, String nickname) {
        if (room == null || nickname == null || nickname.isEmpty()) {
            return;
        }
        if (!DpHandHistoryObservedImpl.isSharkAtTable(room)) {
            return;
        }
        if (DpNpcEngine.SHARK_BOT_NICKNAME.equals(nickname)) {
            return;
        }
        Map<String, DpPlayerStats> statsMap = room.getPlayerStatsMap();
        if (statsMap == null) {
            return;
        }
        if (statsMap.containsKey(nickname)) {
            return;
        }
        try {
            DpSharkOpponentProfile row = mapper.selectByNickname(nickname);
            if (row == null) {
                return;
            }
            if (row.getStatsJson() != null && !row.getStatsJson().isBlank()) {
                StatsPayload sp = objectMapper.readValue(row.getStatsJson(), StatsPayload.class);
                DpPlayerStats stats = new DpPlayerStats();
                stats.restoreFromPersistence(
                        sp.totalHands,
                        sp.totalParticipated,
                        sp.totalRaised,
                        sp.totalShowdown,
                        sp.foldAdjustmentAgainstHero,
                        sp.recentHands,
                        STATS_HAND_WINDOW);
                statsMap.put(nickname, stats);
            }
            if (row.getLearnedJson() != null && !row.getLearnedJson().isBlank()) {
                DpNpcSharkLearningLab.LearnedPersistSnapshot learned = objectMapper.readValue(row.getLearnedJson(),
                        DpNpcSharkLearningLab.LearnedPersistSnapshot.class);
                DpNpcSharkLearningLab.importLearnedSnapshot(nickname, learned);
            }
        } catch (Exception e) {
            log.warn("dp_shark_opponent_profile hydrate failed nickname={}: {}", nickname, e.getMessage());
        }
    }

    /** 新一手开局、座位已定后，为桌上每位非 Shark 玩家尝试加载记忆。 */
    public void hydrateAllOpponentsForNewHand(DpRoom room) {
        if (room == null || !DpHandHistoryObservedImpl.isSharkAtTable(room)) {
            return;
        }
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return;
        }
        for (DpPlayer p : ps) {
            if (p == null) {
                continue;
            }
            hydratePlayerIfNeeded(room, p.getNickname());
        }
    }

    /**
     * 与 DB stats_json 对应的 DTO（字段名稳定，便于版本演进）。
     */
    private static final class StatsPayload {
        public int totalHands;
        public int totalParticipated;
        public int totalRaised;
        public int totalShowdown;
        public double foldAdjustmentAgainstHero;
        public List<DpPlayerStats.SingleHandStats> recentHands = new ArrayList<>();
    }
}
