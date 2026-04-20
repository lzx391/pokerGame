package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.vo.DpRoomPublicRoomsPageVO;

public interface DpRoomHallService {

    void upsertRoomSummary(DpRoom room);

    void deleteRoomSummary(String roomId);

    DpRoomPublicRoomsPageVO getPublicRoomsPage(int page, int pageSize);
}
