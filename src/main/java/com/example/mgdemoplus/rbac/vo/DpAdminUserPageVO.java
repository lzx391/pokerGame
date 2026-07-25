package com.example.mgdemoplus.rbac.vo;

import java.util.ArrayList;
import java.util.List;

public class DpAdminUserPageVO {
    private List<DpAdminUserVO> list = new ArrayList<>();
    private long total;

    public List<DpAdminUserVO> getList() {
        return list;
    }

    public void setList(List<DpAdminUserVO> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
