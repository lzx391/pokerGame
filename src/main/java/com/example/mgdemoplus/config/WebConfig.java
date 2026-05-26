package com.example.mgdemoplus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 本机默认 P: 盘；Docker 等环境用环境变量 MGDEMOPLUS_IMAGES_FILE_LOCATION 覆盖 */
    @Value("${mgdemoplus.images.file-location:file:P:/javaworkspace/DPGameFiles/images/}")
    private String imagesFileLocation;

    /** 曲库音频目录，HTTP 路径为 /music/** */
    @Value("${mgdemoplus.music.file-location:file:P:/javaworkspace/DPGameFiles/music/}")
    private String musicFileLocation;

    /** 其他文件目录，HTTP 路径为 /files/** */
    @Value("${mgdemoplus.files.file-location:file:P:/javaworkspace/DPGameFiles/other/}")
    private String filesFileLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations(normalizeFileLocation(imagesFileLocation));
        registry.addResourceHandler("/music/**")
                .addResourceLocations(normalizeFileLocation(musicFileLocation));
        registry.addResourceHandler("/files/**")
                .addResourceLocations(normalizeFileLocation(filesFileLocation));
    }

    /**
     * 这是用于标准化文件目录路径的方法，供 addResourceHandler(...).addResourceLocations(...) 使用。
     * Spring MVC 静态资源映射机制如下：
     * 例如，addResourceHandler("/music/**").addResourceLocations("file:D:/xxx/")
     * 当有请求 /music/abc.mp3 时，Spring 会自动用 /music/ 开头的路径做匹配，并将余下部分(abc.mp3)拼接到文件目录后面，
     * 即资源查找路径为 D:/xxx/abc.mp3
     * 这个方法就是格式化配置里的路径，保证以 "file:" 开头，以 "/" 结尾，符合 addResourceLocations 的要求。
     */
    private static String normalizeFileLocation(String loc) {
        if (loc == null || loc.isBlank()) {
            // 默认的资源目录
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
