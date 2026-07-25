package com.example.mgdemoplus.rbac.vo;

import java.util.ArrayList;
import java.util.List;

public class DpAdminUserVO {
    private int id;
    private String nickname;
    private List<Long> roleIds = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
