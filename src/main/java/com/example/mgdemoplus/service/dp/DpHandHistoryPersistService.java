package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.entity.dp.DpRoom;

public interface DpHandHistoryPersistService {
    void save(DpObservedHandRecordBO rec, DpRoom room);
}
