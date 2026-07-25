package com.example.mgdemoplus.rbac.impl;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.rbac.DpPermissionService;
import com.example.mgdemoplus.rbac.mapper.DpRbacQueryMapper;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service("dpPermissionService")
public class DpPermissionServiceImpl implements DpPermissionService {

    private static final Logger log = LoggerFactory.getLogger(DpPermissionServiceImpl.class);

    static final String REDIS_KEY_PREFIX = "mgdemo:cache:perm:";
    static final long TTL_SECONDS = 300L;

    @Autowired
    private DpRbacQueryMapper rbacQueryMapper;
    @Autowired
    private DpUserMapper dpUserMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DpCurrentUserSupport currentUserSupport;
/**
 * 本质是输入昵称和需要的权限，通过昵称获取id,再从redis获取权限，然后看包含目标权限吗；然后如果权限在jwt里就直接从jwt中获取权限，再看包不包含
 */
    @Override
    public boolean hasPermi(String code) {
        String nickname = currentUserSupport.requireNickname();
        if (nickname == null || nickname.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        DpUser user = dpUserMapper.selectByNickname(nickname.trim());
        if (user == null) {
            return false;
        }
        return resolveByUserId(user.getId()).contains(code);
    }
/**
 * 通过id获取权限，如果redis有就从redis获取，没有就从数据库获取，然后缓存到redis
 */
    @Override
    public Set<String> resolveByUserId(int userId) {
        if (userId <= 0) {
            return Collections.emptySet();
        }
        String key = REDIS_KEY_PREFIX + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                List<String> codes = objectMapper.readValue(cached, new TypeReference<>() {
                });
                return new LinkedHashSet<>(codes);
            }
        } catch (Exception e) {
            log.warn("Redis get {} failed, loading permissions from DB: {}", key, e.toString());
        }

        List<String> loaded = rbacQueryMapper.selectPermissionCodesByUserId(userId);
        Set<String> result = loaded == null ? Collections.emptySet() : new LinkedHashSet<>(loaded);
        cacheUserPermissions(userId, result);
        return result;
    }

    @Override
    public Set<String> resolveByNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return Collections.emptySet();
        }
        DpUser user = dpUserMapper.selectByNickname(nickname.trim());
        if (user == null) {
            return Collections.emptySet();
        }
        return resolveByUserId(user.getId());
    }

    @Override
    public void evictUser(int userId) {
        if (userId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(REDIS_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis evict perm userId={} failed: {}", userId, e.toString());
        }
    }

    @Override
    public void evictAll() {
        try {
            Set<String> keys = stringRedisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis evict all perm cache failed: {}", e.toString());
        }
    }

    private void cacheUserPermissions(int userId, Set<String> permissions) {
        try {
            stringRedisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + userId,
                    objectMapper.writeValueAsString(permissions),
                    TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set perm userId={} failed: {}", userId, e.toString());
        }
    }
}
