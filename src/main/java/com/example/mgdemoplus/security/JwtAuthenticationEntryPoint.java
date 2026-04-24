package com.example.mgdemoplus.security;

import com.example.mgdemoplus.utils.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 未携带或无法建立登录态时返回 JSON（与 {@link com.example.mgdemoplus.utils.ResultUtil} 风格一致）。
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override // 表示重写父接口的方法（AuthenticationEntryPoint#commence）
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 设置HTTP响应状态为401未认证
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // 响应类型设置为JSON
        response.setCharacterEncoding(StandardCharsets.UTF_8.name()); // 响应编码为UTF-8

        Map<String, Object> body = new LinkedHashMap<>(); // 创建有序Map用于存放响应体
        body.put("success", false); // success字段=false，表示操作失败
        body.put("code", ResultCode.ERROR); // code字段=ResultCode.ERROR，统一错误编码
        body.put("message", "未登录或登录已失效，请重新登录"); // message字段=中文错误提示
        objectMapper.writeValue(response.getOutputStream(), body); // 用ObjectMapper序列化Map为JSON并写入响应输出流
    }
}
