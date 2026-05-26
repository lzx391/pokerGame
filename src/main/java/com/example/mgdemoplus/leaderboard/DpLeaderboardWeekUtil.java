package com.example.mgdemoplus.leaderboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public final class DpLeaderboardWeekUtil {

    private static final DateTimeFormatter WEEK_KEY_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private DpLeaderboardWeekUtil() {
    }

    /** 当前自然周周一（与 {@code mgdemoplus.time-zone} / JVM 默认时区一致）。 */
    public static LocalDate weekStartMonday(ZoneId zone) {
        return LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static String weekKeySuffix(LocalDate weekMonday) {
        return weekMonday.format(WEEK_KEY_FMT);
    }

    public static String handRedisKey(LocalDate weekMonday) {
        return "lb:w:" + weekKeySuffix(weekMonday) + ":hand";
    }

    public static String roomRedisKey(LocalDate weekMonday) {
        return "lb:w:" + weekKeySuffix(weekMonday) + ":room";
    }
}
