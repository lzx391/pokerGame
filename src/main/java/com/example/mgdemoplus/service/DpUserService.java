package com.example.mgdemoplus.service;

import com.example.mgdemoplus.entity.dp.DpUser;

public interface DpUserService {
    int registerUser(DpUser dpUser);

    DpUser selectById(int id);

    String loginUser(String nickname, String password);
}
