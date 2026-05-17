package com.example.mgdemoplus.security;

import com.example.mgdemoplus.service.DpRedisLoginCacheService;
import com.example.mgdemoplus.utils.ResultCode;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
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

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

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
    // doFilterInternal() 方法中调用 applyTokenIfPresentAndValid()
    // applyTokenIfPresentAndValid() 内部解析 token 调用 jwtTokenService.verifyToken()
    // 验证通过后会写入 SecurityContextHolder

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
                logSocialStreamAuth(request);
                filterChain.doFilter(request, response);
                return;
            }

            String token = resolveToken(request);
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }
            try {
                setAuthenticationIfTokenValid(request, token);
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    writeUnauthorized(response, "token 无效或已过期");
                    return;
                }
            } catch (Exception e) {
                // 这里调用 writeUnauthorized() 是本过滤器内主动判断 token 非法/过期时，立刻响应401，让前端知道 token 有问题
                // 这和 JwtAuthenticationEntryPoint.commence() 不同:
                // JwtAuthenticationEntryPoint 通常由 Spring Security 在未认证用户访问受保护资源时自动触发（比如SecurityContext没有用户），
                // 而这里是你提前检测到了有token，但它无效/过期，直接给401回应。
                writeUnauthorized(response, "token 无效或已过期");
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void applyTokenIfPresentAndValid(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            return;
        }
        try {
            setAuthenticationIfTokenValid(request, token);
        } catch (Exception ignored) {
            // 登录页等白名单路径：忽略坏 token，按匿名访问
        }
    }

    private void setAuthenticationIfTokenValid(HttpServletRequest request, String token) {
        Claims claims = jwtTokenService.verifyToken(token);
        String subject = claims.getSubject();
        String jti = claims.getId();
        String cachedJti = dpRedisLoginCacheService.getLoginJti(subject);
        if (subject == null
                || subject.isBlank()
                || cachedJti == null
                || cachedJti.isBlank()
                || !cachedJti.equals(jti)) {
            return;
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(subject, null, null);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    // JwtAuthenticationEntryPoint 的 commence 方法并没有在 JwtAuthenticationFilter 中被直接调用。
    // 它是由 Spring Security 框架在认证失败（例如需要认证但 SecurityContext 没有用户）时自动调用的。
    // 在 SecurityConfig 里 .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
    // 已经把它注册到过滤链。所有未认证请求要进入受保护资源时，Spring Security 会自动触发该入口，
    // 与本过滤器（JwtAuthenticationFilter）并无直接代码调用关系。

    /**
     * 返回 401 未授权 JSON 响应，逐行解释如下：
     */
    /**
     * 解析token的，从普通请求头和SSE连接中拿
     * 优先 {@code Authorization: Bearer}；仅 {@code GET /dp/social/stream} 支持 {@code ?token=}（EventSource 无法设 Header）。
     * 禁止将 token 写入日志。
     */
    private String resolveToken(HttpServletRequest request) {
        String headerToken = jwtTokenService.stripBearerToken(request.getHeader("Authorization"));
        if (headerToken != null) {
            return headerToken;
        }
        if (!isSocialStreamRequest(request)) {
            return null;
        }
        String queryToken = request.getParameter("token");
        if (queryToken == null || queryToken.isBlank()) {
            return null;
        }
        return queryToken.trim();
    }

    private void logSocialStreamAuth(HttpServletRequest request) {
        if (!isSocialStreamRequest(request)) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            log.info("[social-sse] stream filter auth ok nickname={}", auth.getName());
        } else {
            String hasQueryToken = request.getParameter("token") != null ? "yes" : "no";
            log.warn("[social-sse] stream filter no auth queryTokenPresent={}", hasQueryToken);
        }
    }

    private static boolean isSocialStreamRequest(HttpServletRequest request) {
        if (request == null || !"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/dp/social/stream");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        // 1. 设置响应状态码为 401（未授权）
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 2. 设置响应内容类型为 application/json（让前端知道返回的是 JSON 格式）
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // 3. 设置响应的字符编码为 UTF-8（防止中文乱码，规范 JSON 编码）
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // 4. 创建一个 LinkedHashMap 用于有序存放要返回的 JSON 字段
        Map<String, Object> body = new LinkedHashMap<>();

        // 5. 添加字段 "success": false，表示请求未成功
        body.put("success", false);

        // 6. 添加字段 "code"，返回错误码常量（这里是全局结果码中的错误码）
        body.put("code", ResultCode.ERROR);

        // 7. 添加字段 "message"，将具体的错误描述传给前端显示
        body.put("message", message);

        // 8. 使用 ObjectMapper 将上面准备好的 body Map 序列化为 JSON，并写入响应输出流
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
