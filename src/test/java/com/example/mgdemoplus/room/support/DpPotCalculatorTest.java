package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpPot;
import com.example.mgdemoplus.history.bo.DpObservedPotSnapshotBO;
import com.example.mgdemoplus.history.impl.DpHandHistoryObservedServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主池/边池分层与牌谱「有效主池」口径的回归测试。
 */
class DpPotCalculatorTest {

    @Test
    void classicThreeWaySidePots_eligibleShrinksByDepth() {
        List<DpPot> pots = DpPotCalculator.calculate(List.of(
                player("A", 50, false),
                player("B", 100, false),
                player("C", 150, false)));

        assertThat(pots).hasSize(3);
        assertThat(pots.get(0).getAmount()).isEqualTo(150);
        assertThat(pots.get(0).getEligiblePlayers()).containsExactly("A", "B", "C");
        assertThat(pots.get(1).getAmount()).isEqualTo(100);
        assertThat(pots.get(1).getEligiblePlayers()).containsExactly("B", "C");
        assertThat(pots.get(2).getAmount()).isEqualTo(50);
        assertThat(pots.get(2).getEligiblePlayers()).containsExactly("C");
        assertThat(pots.stream().mapToInt(DpPot::getAmount).sum()).isEqualTo(300);
    }

    @Test
    void foldedPlayersAtDifferentLevels_eachPotShowsOnlySurvivor() {
        // 多人陆续弃牌且 totalBet 不同 → 会分出多层池，但每层「有资格」往往只剩最后未弃牌者
        List<DpPot> pots = DpPotCalculator.calculate(List.of(
                player("A", 10, true),
                player("B", 20, true),
                player("C", 30, false)));

        assertThat(pots).hasSize(3);
        for (DpPot pot : pots) {
            assertThat(pot.getEligiblePlayers()).containsExactly("C");
        }
        assertThat(pots.stream().mapToInt(DpPot::getAmount).sum()).isEqualTo(60);
    }

    @Test
    void foldedContributorsStillFundPot_butNotEligible() {
        List<DpPot> pots = DpPotCalculator.calculate(List.of(
                player("A", 100, true),
                player("B", 100, false),
                player("C", 200, false)));

        assertThat(pots).hasSize(2);
        assertThat(pots.get(0).getAmount()).isEqualTo(300);
        assertThat(pots.get(0).getEligiblePlayers()).containsExactly("B", "C");
        assertThat(pots.get(1).getAmount()).isEqualTo(100);
        assertThat(pots.get(1).getEligiblePlayers()).containsExactly("C");
    }

    @Test
    void twoActivePlayersSameDepth_singleMainPot() {
        List<DpPot> pots = DpPotCalculator.calculate(List.of(
                player("A", 40, false),
                player("B", 40, false),
                player("C", 40, true)));

        assertThat(pots).hasSize(1);
        assertThat(pots.get(0).getAmount()).isEqualTo(120);
        assertThat(pots.get(0).getEligiblePlayers()).containsExactly("A", "B");
    }

    @Test
    void effectiveMainPot_excludesSingleEligibleSideLayers() throws Exception {
        List<DpObservedPotSnapshotBO> snap = List.of(
                new DpObservedPotSnapshotBO(150, List.of("A", "B", "C")),
                new DpObservedPotSnapshotBO(100, List.of("B", "C")),
                new DpObservedPotSnapshotBO(50, List.of("C")));

        int effective = invokeEffectiveMainPot(snap, 999);
        assertThat(effective).isEqualTo(250);
    }

    @Test
    void effectiveMainPot_allLayersSingleEligible_fallsBackToTotalPot() throws Exception {
        List<DpObservedPotSnapshotBO> snap = List.of(
                new DpObservedPotSnapshotBO(10, List.of("C")),
                new DpObservedPotSnapshotBO(10, List.of("C")),
                new DpObservedPotSnapshotBO(10, List.of("C")));

        int effective = invokeEffectiveMainPot(snap, 30);
        assertThat(effective).isEqualTo(30);
    }

    private static DpPlayer player(String nick, int totalBet, boolean fold) {
        DpPlayer p = new DpPlayer();
        p.setNickname(nick);
        p.setTotalBet(totalBet);
        p.setFold(fold);
        return p;
    }

    private static int invokeEffectiveMainPot(List<DpObservedPotSnapshotBO> pots, int fallback)
            throws Exception {
        Method m = DpHandHistoryObservedServiceImpl.class.getDeclaredMethod(
                "effectiveMainPotTotalBeforeSettlement", List.class, int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, pots, fallback);
    }
}
