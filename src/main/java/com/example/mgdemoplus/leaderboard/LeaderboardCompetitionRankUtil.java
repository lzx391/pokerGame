package com.example.mgdemoplus.leaderboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class LeaderboardCompetitionRankUtil {

    private LeaderboardCompetitionRankUtil() {
    }

    /**
     * 竞赛排名：同分同 rank，下一项跳号。例：倍数 [10, 10, 9] → rank [1, 1, 3]。
     * 列表须已按倍数降序排列。
     */
    public static <T> void applyCompetitionRanks(List<T> sorted,
                                                 Function<T, BigDecimal> multiplierGetter,
                                                 BiConsumer<T, Integer> rankSetter) {
        if (sorted == null || sorted.isEmpty()) {
            return;
        }
        int rank = 0;
        BigDecimal prev = null;
        for (int i = 0; i < sorted.size(); i++) {
            T entry = sorted.get(i);
            BigDecimal m = multiplierGetter.apply(entry);
            if (prev == null || m.compareTo(prev) != 0) {
                rank = i + 1;
                prev = m;
            }
            rankSetter.accept(entry, rank);
        }
    }
}
