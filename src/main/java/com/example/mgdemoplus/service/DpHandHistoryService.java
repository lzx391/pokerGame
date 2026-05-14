package com.example.mgdemoplus.service;

import com.example.mgdemoplus.vo.DpHandHistoryDetailVO;
import com.example.mgdemoplus.vo.DpHandHistoryPageVO;

public interface DpHandHistoryService {
    DpHandHistoryPageVO checkUserAndOtherPlayerHandHistoryList(
            Integer userId,
            Integer otherUserId,
            int page,
            int pageSize
    );

    DpHandHistoryPageVO listMyHandsPage(Integer userId, int page, int pageSize);

    DpHandHistoryDetailVO getDetail(long handHistoryId, Integer userId);
} 
