package com.example.mgdemoplus.leaderboard;

import com.example.mgdemoplus.leaderboard.vo.WeeklyLeaderboardItemVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardCompetitionRankUtilTest {

    @Test
    void applyCompetitionRanks_tiedScores_shareRankAndSkip() {
        List<WeeklyLeaderboardItemVO> items = new ArrayList<>();
        items.add(item(10));
        items.add(item(10));
        items.add(item(9));

        LeaderboardCompetitionRankUtil.applyCompetitionRanks(
                items, WeeklyLeaderboardItemVO::getMultiplier, WeeklyLeaderboardItemVO::setRank);

        assertThat(items.get(0).getRank()).isEqualTo(1);
        assertThat(items.get(1).getRank()).isEqualTo(1);
        assertThat(items.get(2).getRank()).isEqualTo(3);
    }

    @Test
    void applyCompetitionRanks_topThreeCutoffIncludesRankThree() {
        List<WeeklyLeaderboardItemVO> items = new ArrayList<>();
        items.add(item(30));
        items.add(item(20));
        items.add(item(10));
        items.add(item(5));

        LeaderboardCompetitionRankUtil.applyCompetitionRanks(
                items, WeeklyLeaderboardItemVO::getMultiplier, WeeklyLeaderboardItemVO::setRank);

        long topThree = items.stream().filter(i -> i.getRank() <= 3).count();
        assertThat(topThree).isEqualTo(3);
        assertThat(items.get(3).getRank()).isEqualTo(4);
    }

    private static WeeklyLeaderboardItemVO item(double multiplier) {
        WeeklyLeaderboardItemVO vo = new WeeklyLeaderboardItemVO();
        vo.setMultiplier(BigDecimal.valueOf(multiplier));
        return vo;
    }
}
