package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Offloads post-settle MySQL writes (hand history, user stats, weekly board) from the bet/fold thread.
 */
@Component
public class DpSettlePersistenceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DpSettlePersistenceDispatcher.class);

    private final DpHandHistoryPersistService observedHandPersistService;
    private final DpUserStatsMapper dpUserStatsMapper;
    private final DpLeaderboardWeeklyWriteService dpLeaderboardWeeklyWriteService;
    private final Executor executor;
    private final boolean asyncEnabled;

    public DpSettlePersistenceDispatcher(
            DpHandHistoryPersistService observedHandPersistService,
            DpUserStatsMapper dpUserStatsMapper,
            DpLeaderboardWeeklyWriteService dpLeaderboardWeeklyWriteService,
            @Qualifier("dpSettlePersistExecutor") Executor executor,
            @Value("${mgdemoplus.settle-persist.async-enabled:true}") boolean asyncEnabled) {
        this.observedHandPersistService = observedHandPersistService;
        this.dpUserStatsMapper = dpUserStatsMapper;
        this.dpLeaderboardWeeklyWriteService = dpLeaderboardWeeklyWriteService;
        this.executor = executor;
        this.asyncEnabled = asyncEnabled;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    /**
     * Enqueue or run synchronously depending on {@code mgdemoplus.settle-persist.async-enabled}.
     */
    public void dispatch(DpSettlePersistJob job) {
        if (job == null || job.archived() == null) {
            return;
        }
        if (!asyncEnabled) {
            persistOnce(job, false);
            return;
        }
        try {
            executor.execute(() -> persistWithRetry(job));
        } catch (RejectedExecutionException ex) {
            log.warn("settle persist queue full roomId={}, sync fallback: {}", job.roomId(), ex.toString());
            persistWithRetry(job);
        }
    }

    @PreDestroy
    void drainOnShutdown() {
        if (!(executor instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tex)) {
            return;
        }
        tex.shutdown();
        try {
            if (!tex.getThreadPoolExecutor().awaitTermination(3, TimeUnit.SECONDS)) {
                log.warn("settle persist executor drain timeout; {} task(s) may still run",
                        tex.getThreadPoolExecutor().getQueue().size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void persistWithRetry(DpSettlePersistJob job) {
        if (persistOnce(job, true)) {
            return;
        }
        log.warn("settle persist failed once roomId={} handSeed={}, retrying", job.roomId(), job.archived().handSeed);
        if (!persistOnce(job, true)) {
            log.error("settle persist failed after retry roomId={} handSeed={}", job.roomId(), job.archived().handSeed);
        }
    }

    private boolean persistOnce(DpSettlePersistJob job, boolean fromAsyncWorker) {
        try {
            observedHandPersistService.save(job.archived(), job.roomSnapshotForParticipants());
            applyStatsIncrements(job.statsIncrements());
            return true;
        } catch (Exception e) {
            if (fromAsyncWorker) {
                log.warn("settle persist error roomId={} handSeed={}: {}", job.roomId(), job.archived().handSeed,
                        e.toString());
            } else {
                log.warn("settle persist sync error roomId={} handSeed={}: {}", job.roomId(), job.archived().handSeed,
                        e.toString());
            }
            return false;
        }
    }

    private void applyStatsIncrements(List<DpSettleStatsIncrement> increments) {
        if (increments == null || increments.isEmpty()) {
            return;
        }
        for (DpSettleStatsIncrement inc : increments) {
            if (inc == null) {
                continue;
            }
            dpUserStatsMapper.upsertAfterHand(
                    inc.userId(), inc.royalInc(), inc.straightInc(), inc.fourInc(), inc.handMultiplier());
            if (inc.handMultiplier() != null && inc.handMultiplier().signum() > 0) {
                dpLeaderboardWeeklyWriteService.recordHandBest(inc.userId(), inc.handMultiplier());
            }
        }
    }

    /** Minimal {@link DpRoomBO} copy for participant insert (immutable vs live table). */
    public static DpRoomBO snapshotRoomForHandPersist(DpRoomBO live) {
        if (live == null) {
            return null;
        }
        DpRoomBO snap = new DpRoomBO();
        snap.setRoomId(live.getRoomId());
        if (live.getPlayers() == null) {
            return snap;
        }
        List<com.example.mgdemoplus.common.entity.DpPlayer> copies = new java.util.ArrayList<>();
        for (com.example.mgdemoplus.common.entity.DpPlayer p : live.getPlayers()) {
            if (p == null) {
                continue;
            }
            com.example.mgdemoplus.common.entity.DpPlayer c = new com.example.mgdemoplus.common.entity.DpPlayer();
            c.setNickname(p.getNickname());
            c.setDpUserId(p.getDpUserId());
            c.setDealer(p.isDealer());
            c.setBlind(p.getBlind());
            copies.add(c);
        }
        snap.setPlayers(copies);
        return snap;
    }
}
