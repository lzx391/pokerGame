package com.example.mgdemoplus.quickmatch.pairing;

/**
 * 默认快匹 FIFO 队列中的一条等待记录（与 {@link DpRoomServiceImpl} 中
 * {@code defaultQmWaiters} 元素语义一致；供配对协调器与 Service 共用）。
 */
public record DpQuickMatchWaitEntry(String nickname, Integer userId, long enqueuedMs) {
}
