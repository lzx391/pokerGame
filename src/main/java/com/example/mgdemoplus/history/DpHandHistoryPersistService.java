package com.example.mgdemoplus.history;

import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.common.bo.DpRoomBO;

public interface DpHandHistoryPersistService {
    /**
     * @return 写入成功时返回 {@code dp_observed_hand_history.id}，失败或未写入时 {@code null}
     */
    Long save(DpObservedHandRecordBO rec, DpRoomBO room);
}