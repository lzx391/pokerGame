package com.example.mgdemoplus.room.support;

/** Peak win streak to persist when a player breaks a streak on hand settle. */
public record DpMaxWinStreakFlush(int userId, int streak) {
}
