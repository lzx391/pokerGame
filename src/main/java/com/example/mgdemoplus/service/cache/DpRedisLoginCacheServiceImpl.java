 package com.example.mgdemoplus.service.cache;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.mgdemoplus.security.JwtTokenService;
import com.example.mgdemoplus.service.dp.DpRedisLoginCacheService;
@Service
public class DpRedisLoginCacheServiceImpl implements DpRedisLoginCacheService {

    /**
     * 登录后在 Redis 中存放当前会话 jti 的 key 前缀，需与过滤器读取一致：
     * {@code prefix + nickname}。
     */
    static final String REDIS_LOGIN_JTI_KEY_PREFIX = "mgdemo:cache:login:";
    @Autowired
    private  StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtTokenService jwtTokenService;
    public void setLoginJti(String nickname, String jti) {
        stringRedisTemplate.opsForValue().set(REDIS_LOGIN_JTI_KEY_PREFIX + nickname, jti,jwtTokenService.tokenTtlSeconds(),TimeUnit.SECONDS);
    }

    public String getLoginJti(String nickname) {
        return stringRedisTemplate.opsForValue().get(REDIS_LOGIN_JTI_KEY_PREFIX + nickname);
    }
    /**
     * 踢人服务需要删除redis中的jti
     */
    public void removeLoginJti(String nickname) {
        stringRedisTemplate.delete(REDIS_LOGIN_JTI_KEY_PREFIX + nickname);
    }
}