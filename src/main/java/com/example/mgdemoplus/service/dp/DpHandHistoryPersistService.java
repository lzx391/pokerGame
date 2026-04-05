package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.service.serviceImpl.dp.DpHandHistoryObservedImpl;

public interface DpHandHistoryPersistService {
     void save(DpHandHistoryObservedImpl.ObservedHandRecord rec, DpRoom room);
    
}