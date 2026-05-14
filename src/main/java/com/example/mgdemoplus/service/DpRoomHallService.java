package com.example.mgdemoplus.service;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.bo.DpRoomLobbySearchParamBO;
import com.example.mgdemoplus.vo.DpRoomPublicRoomsPageVO;

import java.util.Set;

public interface DpRoomHallService {

    void upsertRoomSummary(DpRoomBO room);

    void deleteRoomSummary(String roomId);

    /**
     * 软删除大厅里「当前进程内存中已不存在」的房间行（典型：服务重启后 dp_room_lobby 残留）。
     * 仅适用于单机内存房模型；多实例各自一套 roomMap 时不要开启定时对齐。
     *
     * @param runtimeRoomIds 当前 JVM {@code roomMap} 中的房间 ID
     * @return 实际软删除的行数
     */
    int reconcileLobbyWithRuntimeRoomIds(Set<String> runtimeRoomIds);

    DpRoomPublicRoomsPageVO getPublicRoomsPage(int page, int pageSize);

    /**
     * 筛选 + 精确房间号：MyBatis-Plus 查库；结果按「版本号 + 参数指纹」缓存在 Redis（与 {@link #getPublicRoomsPage} 共用 rev）。
     */
    DpRoomPublicRoomsPageVO queryPublicRoomsFromDb(DpRoomLobbySearchParamBO param);
}
