package com.example.mgdemoplus.security;

import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.user.cache.DpRedisLoginCacheService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * WebSocket 握手无 {@link org.springframework.security.core.context.SecurityContextHolder}，
 * 从查询参数 {@code ?token=} 校验 JWT 并解析用户。
 */
@Component
public class DpWebSocketAuthSupport {

    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private DpRedisLoginCacheService dpRedisLoginCacheService;
    @Autowired
    private DpUserMapper dpUserMapper;

    /**
     * 校验 token 签名、过期与 Redis JTI，返回对应 {@code dp_user}。
     */
    public Optional<DpUser> verifyTokenFromQuery(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtTokenService.verifyToken(token.trim());
            String subject = claims.getSubject();
            String jti = claims.getId();
            String cachedJti = dpRedisLoginCacheService.getLoginJti(subject);
            if (subject == null
                    || subject.isBlank()
                    || cachedJti == null
                    || cachedJti.isBlank()
                    || !cachedJti.equals(jti)) {
                return Optional.empty();
            }
            DpUser u = dpUserMapper.selectByNickname(subject.trim());
            return Optional.ofNullable(u);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
