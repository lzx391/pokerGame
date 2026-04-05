package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.dto.DpObservedHandRecordDTO;
import com.example.mgdemoplus.entity.dp.DpRoom;

public interface DpHandHistoryPersistService {
    void save(DpObservedHandRecordDTO rec, DpRoom room);
}
