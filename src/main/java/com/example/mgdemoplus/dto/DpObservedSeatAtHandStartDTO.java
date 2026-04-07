package com.example.mgdemoplus.dto;

/**
 * 盲注后、行动开始前每位玩家在桌状态（含座位序）。
 */
public final class DpObservedSeatAtHandStartDTO {
    public final int seatIndex;
    public final String nickname;
    /** 0 无 1 SB 2 BB */
    public final int blind;
    public final int chipsAfterBlinds;

    public DpObservedSeatAtHandStartDTO(int seatIndex, String nickname, int blind, int chipsAfterBlinds) {
        this.seatIndex = seatIndex;
        this.nickname = nickname == null ? "" : nickname;
        this.blind = blind;
        this.chipsAfterBlinds = chipsAfterBlinds;
    }
}
