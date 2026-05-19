package com.example.mgdemoplus.moderation;

/**
 * 敏感词：昵称类拒绝；发言类对外展示时替换为 {@code *}。
 * <p>聊天/私信须<strong>存库原文</strong>，仅在 WebSocket 广播、列表接口等出口调用 {@link #maskForChat}。</p>
 */
public interface DpSensitiveWordService {

    /** 昵称/注册名等：命中则不应通过 */
    boolean containsSensitive(String text);

    /** 对外展示用：命中片段替换为等长 {@code *}（勿用于落库） */
    String maskForChat(String text);
}
