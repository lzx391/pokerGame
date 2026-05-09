package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
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
 * 将 {@link DpPlayerStats} 与（实验用）{@link DpNpcSharkLearningLab} 旋钮快照按玩家昵称持久化，
 * 跨随机房间认出同一昵称并恢复习惯画像；不依赖桌面上是否有 BOT_Shark。
 *
 * <p>
 * 总开关：{@link DpNpcEngine#NPC_SHARK_OPPONENT_DB_ENABLED}；为 {@code false} 时不读写 DB，
 * 牌谱归档仍由其它模块负责。
 * </p>
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
     * 一手结算后调用：为桌上每位真人/Bot 昵称 upsert 一行（旋钮快照由 LearningLab 内存状态导出）。
     */
    public void persistOpponentsAfterHand(DpRoomBO room) {
        if (!DpNpcEngine.NPC_SHARK_OPPONENT_DB_ENABLED) {
            return;
        }
        if (room == null) {
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
     * 若该昵称在房内尚无内存统计，则从 DB 恢复（避免覆盖本局已累积的 map）。
     */
    public void hydratePlayerIfNeeded(DpRoomBO room, String nickname) {
        if (!DpNpcEngine.NPC_SHARK_OPPONENT_DB_ENABLED) {
            return;
        }
        if (room == null || nickname == null || nickname.isEmpty()) {
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

    /** 新一手开局、座位已定后，为桌上每位玩家尝试加载记忆。 */
    public void hydrateAllOpponentsForNewHand(DpRoomBO room) {
        if (room == null) {
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
