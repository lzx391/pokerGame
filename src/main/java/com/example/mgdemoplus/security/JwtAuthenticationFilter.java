package com.example.mgdemoplus.security;

import com.example.mgdemoplus.service.dp.DpRedisLoginCacheService;
import com.example.mgdemoplus.utils.ResultCode;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从 {@code Authorization: Bearer} 解析 JWT，写入 {@link SecurityContextHolder}。
 * 白名单路径不强制登录；白名单上若带合法 token 仍会写入身份，便于后续扩展。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RequestMatcher publicPaths;
    private final ObjectMapper objectMapper;
    private final JwtTokenService jwtTokenService;
    @Autowired
    private DpRedisLoginCacheService dpRedisLoginCacheService;

    public JwtAuthenticationFilter(RequestMatcher publicPaths, ObjectMapper objectMapper, JwtTokenService jwtTokenService) {
        this.publicPaths = publicPaths;
        this.objectMapper = objectMapper;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (publicPaths.matches(request)) {
                /**
                 * 白名单路径不强制登录；白名单上若带合法 token 仍会写入身份，便于后续扩展。
                 */
                applyTokenIfPresentAndValid(request);
                filterChain.doFilter(request, response);
                return;
            }

            String token = jwtTokenService.stripBearerToken(request.getHeader("Authorization"));
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }
            try {
                Claims claims = jwtTokenService.verifyToken(token);
                String subject = claims.getSubject();
                String jti = claims.getId();
                String cach_jti = dpRedisLoginCacheService.getLoginJti(subject);
                if (subject != null && !subject.isBlank() && cach_jti != null && !cach_jti.isBlank() && cach_jti.equals(jti)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(subject, null, null);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                writeUnauthorized(response, "token 无效或已过期");
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void applyTokenIfPresentAndValid(HttpServletRequest request) {
        String token = jwtTokenService.stripBearerToken(request.getHeader("Authorization"));
        if (token == null) {
            return;
        }
        try {
            Claims claims = jwtTokenService.verifyToken(token);
            String subject = claims.getSubject();
            if (subject != null && !subject.isBlank()) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(subject, null, null);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ignored) {
            // 登录页等白名单路径：忽略坏 token，按匿名访问
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", ResultCode.ERROR);
        body.put("message", message);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
