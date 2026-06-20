package com.example.mgdemoplus.rbac;

import java.util.Set;

public interface DpPermissionService {

    boolean hasPermi(String code);

    Set<String> resolveByUserId(int userId);

    Set<String> resolveByNickname(String nickname);

    void evictUser(int userId);

    void evictAll();
}
