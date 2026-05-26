package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;

import java.util.List;

/** Immutable settle persistence payload (safe to process off the game thread). */
public record DpSettlePersistJob(
        String roomId,
        DpObservedHandRecordBO archived,
        DpRoomBO roomSnapshotForParticipants,
        List<DpSettleStatsIncrement> statsIncrements) {
}
