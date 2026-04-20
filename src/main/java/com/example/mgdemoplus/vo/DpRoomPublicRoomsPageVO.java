package com.example.mgdemoplus.vo;

import java.util.ArrayList;
import java.util.List;

import com.example.mgdemoplus.entity.dp.DpRoom;

public class DpRoomPublicRoomsPageVO {
    private List<DpRoom> list = new ArrayList<>();
    private long total;
    private int page;
    private int pageSize;

    public static DpRoomPublicRoomsPageVO empty(int page, int pageSize) {
        DpRoomPublicRoomsPageVO out = new DpRoomPublicRoomsPageVO();
        out.setPage(page);
        out.setPageSize(pageSize);
        out.setTotal(0);
        out.setList(new ArrayList<>());
        return out;
    }

    public List<DpRoom> getList() {
        return list;
    }

    public void setList(List<DpRoom> list) {
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
