package com.example.mgdemoplus.bo;

/**
 * GET 查询参数绑定：筛选 + 可选精确房间号（与 {@code /dpRoom/publicRooms/query} 对应）。
 */
public class DpRoomLobbySearchParamBO {

    private int page = 1;
    private int pageSize = 10;

    /** 精确房间号；非空时与其它筛选同时生效（通常只命中 0～1 条） */
    private String roomId;

    private Integer minBigBlindChips;
    private Integer maxBigBlindChips;

    private Integer minPlayerCount;
    private Integer maxPlayerCount;

    /** null 表示不筛选；true/false 表示仅要/不要密码房 */
    private Boolean passwordProtected;

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

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Integer getMinBigBlindChips() {
        return minBigBlindChips;
    }

    public void setMinBigBlindChips(Integer minBigBlindChips) {
        this.minBigBlindChips = minBigBlindChips;
    }

    public Integer getMaxBigBlindChips() {
        return maxBigBlindChips;
    }

    public void setMaxBigBlindChips(Integer maxBigBlindChips) {
        this.maxBigBlindChips = maxBigBlindChips;
    }

    public Integer getMinPlayerCount() {
        return minPlayerCount;
    }

    public void setMinPlayerCount(Integer minPlayerCount) {
        this.minPlayerCount = minPlayerCount;
    }

    public Integer getMaxPlayerCount() {
        return maxPlayerCount;
    }

    public void setMaxPlayerCount(Integer maxPlayerCount) {
        this.maxPlayerCount = maxPlayerCount;
    }

    public Boolean getPasswordProtected() {
        return passwordProtected;
    }

    public void setPasswordProtected(Boolean passwordProtected) {
        this.passwordProtected = passwordProtected;
    }
}
