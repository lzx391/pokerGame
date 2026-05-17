package com.example.mgdemoplus.service;

import com.example.mgdemoplus.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.bo.DpRoomBO;

public interface DpHandHistoryPersistService {
    void save(DpObservedHandRecordBO rec, DpRoomBO room);
}
