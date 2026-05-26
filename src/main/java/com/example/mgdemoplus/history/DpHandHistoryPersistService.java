package com.example.mgdemoplus.history;

import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.common.bo.DpRoomBO;

public interface DpHandHistoryPersistService {
    void save(DpObservedHandRecordBO rec, DpRoomBO room);
}