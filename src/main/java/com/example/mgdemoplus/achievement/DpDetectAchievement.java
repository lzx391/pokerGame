package com.example.mgdemoplus.achievement;

import com.example.mgdemoplus.room.support.DpSettlePersistJob;

public interface DpDetectAchievement {
    void detect(DpSettlePersistJob job, Long handHistoryId);
}
