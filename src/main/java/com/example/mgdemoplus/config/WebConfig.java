package com.example.mgdemoplus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 只要访问路径是以 /images/ 开头
        registry.addResourceHandler("/images/**")
                // 就去这个物理路径下找（注意 file: 前缀）
                .addResourceLocations("file:P:/javaworkspace/MGDemoPlusFiles/");
    }
}
