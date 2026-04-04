package com.example.mgdemoplus.service;

import com.example.mgdemoplus.dto.DpHandHistoryDetailDTO;
import com.example.mgdemoplus.dto.DpHandHistoryPageDTO;

public interface DpHandHistoryService {
    public DpHandHistoryPageDTO checkUserAndOtherPlayerHandHistoryList(
        Integer userId,
        String otherNickname,
        String nickname,
        Integer otherUserId,
        int page,
        int pageSize
);
public DpHandHistoryPageDTO listMyHandsPage(Integer userId, String nickname, int page, int pageSize);

public DpHandHistoryDetailDTO getDetail(long handHistoryId, Integer userId, String nickname);
} 
