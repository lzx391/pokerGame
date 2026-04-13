package com.example.mgdemoplus.utils;

/**
 * 读取进程环境变量；若未设置则回退到 {@link System#getProperty(String)}（供 {@code LocalDotenvLoader} 注入的 .env 使用）。
 */
public final class EnvHelper {

    private EnvHelper() {}

    public static String getenvOrProperty(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String e = System.getenv(key);
        if (e != null && !e.isBlank()) {
            return e;
        }
        String p = System.getProperty(key);
        return p != null && !p.isBlank() ? p : "";
    }
}
