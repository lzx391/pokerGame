package com.example.mgdemoplus.dto;

import java.util.List;

/**
 * 大厅公开房间列表（分页）；与 {@code GET /dpRoom/publicRooms} 响应体一致。
 */
public class DpPublicRoomsPageDTO {

    private List<DpRoomDTO> list;
    private long total;
    private int page;
    private int pageSize;

    public List<DpRoomDTO> getList() {
        return list;
    }

    public void setList(List<DpRoomDTO> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
