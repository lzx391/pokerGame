package com.example.mgdemoplus.dto;

import java.util.List;

/**
 * 历史对局分页结果（无第三方分页插件，由 SQL LIMIT + COUNT 实现）。
 */
public class DpHandHistoryPageDTO {
    /** 符合条件的总行数（参与者维度） */
    private long total;
    /** 当前页码，从 1 开始 */
    private int page;
    /** 每页条数 */
    private int pageSize;
    private List<DpHandHistoryListItemDTO> records;

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

    public List<DpHandHistoryListItemDTO> getRecords() {
        return records;
    }

    public void setRecords(List<DpHandHistoryListItemDTO> records) {
        this.records = records;
    }
}
