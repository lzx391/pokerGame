package com.example.mgdemoplus.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * 本机开发：在 Spring 启动前读取<strong>当前工作目录</strong>下的 {@code .env}，写入 {@link System#setProperty}，
 * 仅当 {@link System#getenv(String)} 尚未设置同名变量时生效，避免覆盖机器 / IDE 里已配置的环境变量。
 * <p>
 * Docker：由 {@code docker compose} 自行读取同目录 {@code .env} 并注入容器进程环境，一般<strong>不会</strong>执行本类；
 * 若镜像内未带 {@code .env} 文件，也不影响（{@code ignoreIfMissing}）。
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
            // 格式异常或无权读文件：沿用 application.properties / 系统环境变量
        }
    }
}
