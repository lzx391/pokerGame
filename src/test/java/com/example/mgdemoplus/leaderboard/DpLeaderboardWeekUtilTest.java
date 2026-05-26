package com.example.mgdemoplus.leaderboard;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;

class DpLeaderboardWeekUtilTest {

    @Test
    void weekMondayKey_previousOrSameMonday() {
        LocalDate wed = LocalDate.of(2026, 5, 20);
        assertThat(wed.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(wed.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .isEqualTo(LocalDate.of(2026, 5, 18));
    }

    @Test
    void redisKeys_useYyyyMmDdSuffix() {
        LocalDate mon = LocalDate.of(2026, 5, 18);
        assertThat(DpLeaderboardWeekUtil.handRedisKey(mon)).isEqualTo("lb:w:20260518:hand");
        assertThat(DpLeaderboardWeekUtil.roomRedisKey(mon)).isEqualTo("lb:w:20260518:room");
    }
}
