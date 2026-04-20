package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.bo.DpRoomLobbySearchParamBO;
import com.example.mgdemoplus.vo.DpRoomPublicRoomsPageVO;

public interface DpRoomHallService {

    void upsertRoomSummary(DpRoomBO room);

    void deleteRoomSummary(String roomId);

    DpRoomPublicRoomsPageVO getPublicRoomsPage(int page, int pageSize);

    /**
     * 筛选 + 精确房间号：只查 MySQL（MyBatis-Plus），不使用 Redis。
     */
    DpRoomPublicRoomsPageVO queryPublicRoomsFromDb(DpRoomLobbySearchParamBO param);
}
