package com.example.mgdemoplus.config;

import com.example.mgdemoplus.security.JwtAuthenticationEntryPoint;
import com.example.mgdemoplus.security.JwtAuthenticationFilter;
import com.example.mgdemoplus.security.JwtSecurityConstants;
import com.example.mgdemoplus.security.JwtTokenService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public RequestMatcher jwtPublicPathsMatcher() {
        List<RequestMatcher> matchers = new ArrayList<>();
        for (String pattern : JwtSecurityConstants.PERMIT_ALL) {
            matchers.add(new AntPathRequestMatcher(pattern));
        }
        matchers.add(PathRequest.toStaticResources().atCommonLocations());
        return new OrRequestMatcher(matchers);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(RequestMatcher jwtPublicPathsMatcher,
            ObjectMapper objectMapper,
            JwtTokenService jwtTokenService) {
        return new JwtAuthenticationFilter(jwtPublicPathsMatcher, objectMapper, jwtTokenService);
    }

    /**
     * 配置Spring Security的SecurityFilterChain。
     *
     * 语法现象说明：
     * - @Bean注解：声明该方法返回的SecurityFilterChain对象会被Spring托管，注入到IoC容器。
     * - 方法入参 HttpSecurity http：Spring Security用于配置安全相关设置的核心对象。
     * - 方法体采用链式调用（fluent API），每个方法返回自身或下一个配置器，便于连续配置。
     * 
     * http
     *   .csrf(AbstractHttpConfigurer::disable)：禁用CSRF防护，::为方法引用语法，等价于 h -> h.disable()。
     *   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))：
     *       配置为无状态，lambda表达式"参数 -> 操作"。
     *   .authorizeHttpRequests(auth -> ...)：授权配置，auth是lambda输入参数。
     *       - .requestMatchers(...).permitAll()：指定的路径允许所有请求通过。
     *       - .anyRequest().authenticated()：其余任意请求需认证。
     *   .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))：
     *       配置未认证时的处理逻辑，lambda表达式形式。
     *   .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)：
     *       在指定的过滤器之前加入自定义JWT过滤器。
     * return http.build()：最终生成SecurityFilterChain对象。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // 禁用CSRF防护，使用方法引用语法(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                // 设置会话创建策略为无状态
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置授权规则，采用lambda表达式
                .authorizeHttpRequests(auth -> auth
                        // 允许PERMIT_ALL中定义的路径全部通过
                        .requestMatchers(JwtSecurityConstants.PERMIT_ALL).permitAll()
                        // 允许静态资源路径全部通过
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        // 其他所有请求需要认证
                        .anyRequest().authenticated())
                // 配置异常处理（未认证入口），使用lambda表达式
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // 在用户名/密码过滤器之前添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // 构建SecurityFilterChain
        return http.build();
    }
}
