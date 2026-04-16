package com.example.mgdemoplus.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * 在 {@link com.example.mgdemoplus.MgDemoPlusApplication#main} 里、Spring 启动前读取仓库根目录 {@code .env}，
 * 写入 {@link System#setProperty}（仅当 {@link System#getenv(String)} 尚未设置同名变量时）。
 * Spring 解析 {@code application.properties} 与 {@code @Value} 时会用到这些属性。
 * <p>
 * Docker：由 {@code docker compose} 读宿主机的 {@code .env} 并注入容器环境，一般不在容器内执行本类。
 */
public final class LocalDotenvLoader {

    private LocalDotenvLoader() {}

    public static void load() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || key.isBlank()) {
                    return;
                }
                if (val == null) {
                    return;
                }
                String existing = System.getenv(key);
                if (existing != null && !existing.isBlank()) {
                    return;
                }
                System.setProperty(key, val);
            });
        } catch (Exception ignored) {
            // 无文件或解析失败：沿用 application.properties
        }
    }
}
