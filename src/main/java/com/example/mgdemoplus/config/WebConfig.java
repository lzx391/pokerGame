package com.example.mgdemoplus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 本机默认 P: 盘；Docker 等环境用环境变量 MGDEMOPLUS_IMAGES_FILE_LOCATION 覆盖 */
    @Value("${mgdemoplus.images.file-location:file:P:/javaworkspace/DPGameFiles/}")
    private String imagesFileLocation;

    /** 曲库音频目录，HTTP 路径为 /music/** */
    @Value("${mgdemoplus.music.file-location:file:P:/javaworkspace/DPGameFiles/music/}")
    private String musicFileLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations(normalizeFileLocation(imagesFileLocation));
        registry.addResourceHandler("/music/**")
                .addResourceLocations(normalizeFileLocation(musicFileLocation));
    }

    private static String normalizeFileLocation(String loc) {
        if (loc == null || loc.isBlank()) {
            return "file:P:/javaworkspace/DPGameFiles/";
        }
        String s = loc.trim();
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        if (!s.startsWith("file:")) {
            s = "file:" + s;
        }
        return s;
    }
}
