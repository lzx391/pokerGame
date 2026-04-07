package com.example.mgdemoplus.config;

import com.example.mgdemoplus.security.JwtAuthenticationEntryPoint;
import com.example.mgdemoplus.security.JwtAuthenticationFilter;
import com.example.mgdemoplus.security.JwtSecurityConstants;

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
                                                           ObjectMapper objectMapper) {
        return new JwtAuthenticationFilter(jwtPublicPathsMatcher, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(JwtSecurityConstants.PERMIT_ALL).permitAll()
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
