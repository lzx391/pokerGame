package com.example.mgdemoplus.history;

import com.example.mgdemoplus.history.vo.DpHandHistoryDetailVO;
import com.example.mgdemoplus.history.vo.DpHandHistoryPageVO;

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