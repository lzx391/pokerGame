package com.example.mgdemoplus.presence.cache;

public interface DpRedisLoginCacheService {
    public void setLoginJti(String nickname, String jti);
    public String getLoginJti(String nickname);
    public void removeLoginJti(String nickname);
}
