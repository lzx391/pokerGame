package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.dto.DpHandHistoryDetailDTO;
import com.example.mgdemoplus.dto.DpHandHistoryPageDTO;

public interface DpHandHistoryService {
    DpHandHistoryPageDTO checkUserAndOtherPlayerHandHistoryList(
            Integer userId,
            Integer otherUserId,
            int page,
            int pageSize
    );

    DpHandHistoryPageDTO listMyHandsPage(Integer userId, int page, int pageSize);

    DpHandHistoryDetailDTO getDetail(long handHistoryId, Integer userId);
} 
