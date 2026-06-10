package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.achievement.DpDetectAchievement;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpSettlePersistenceDispatcherTest {

    private static final Long HAND_HISTORY_ID = 555L;

    @Mock
    private DpHandHistoryPersistService observedHandPersistService;
    @Mock
    private DpUserStatsMapper dpUserStatsMapper;
    @Mock
    private DpLeaderboardWeeklyWriteService dpLeaderboardWeeklyWriteService;
    @Mock
    private DpDetectAchievement dpDetectAchievement;

    @Test
    @DisplayName("同步持久化：先落牌谱再检测成就并传入 handHistoryId")
    void persistOnce_savesHandHistoryBeforeDetect() {
        DpSettlePersistenceDispatcher dispatcher = new DpSettlePersistenceDispatcher(
                observedHandPersistService,
                dpUserStatsMapper,
                dpLeaderboardWeeklyWriteService,
                r -> r.run(),
                false,
                dpDetectAchievement);

        DpObservedHandRecordBO archived = new DpObservedHandRecordBO(
                "room-1", 1L, 0L, 1L, 10, 20, 0, "dealer",
                List.of(), List.of(), List.of(), List.of(), 100,
                Map.of(), Map.of(), Map.of());
        DpRoomBO room = new DpRoomBO();
        DpPlayer human = new DpPlayer();
        human.setNickname("hero");
        human.setDpUserId(1);
        room.setPlayers(List.of(human));
        DpSettlePersistJob job = new DpSettlePersistJob("room-1", archived, room, List.of(), List.of());

        when(observedHandPersistService.save(same(archived), same(room))).thenReturn(HAND_HISTORY_ID);

        dispatcher.dispatch(job);

        InOrder inOrder = inOrder(observedHandPersistService, dpDetectAchievement);
        inOrder.verify(observedHandPersistService).save(same(archived), same(room));
        inOrder.verify(dpDetectAchievement).detect(same(job), eq(HAND_HISTORY_ID));
    }
}
