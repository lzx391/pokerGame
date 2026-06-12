package com.example.mgdemoplus.npc.eval;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcEquityEstimatorTest {

    @ParameterizedTest(name = "#{index} {0}")
    @MethodSource("equityCases")
    void equityWithinPlanRange(
            String label,
            List<String> hole,
            List<String> board,
            String stage,
            double minEq,
            double maxEq) {
        DpNpcHandSnapshot snap = DpNpcHandClassifier.classify(hole, board, stage);
        double eq = DpNpcEquityEstimator.estimate(snap, stage, hole);
        assertTrue(eq >= minEq && eq <= maxEq,
                label + " equity " + eq + " not in [" + minEq + "," + maxEq + "]");
    }

    static Stream<Arguments> equityCases() {
        return Stream.of(
                eq("1", h("2h_3h"), b("6s_Ah_Kd"), "flop", 0.18, 0.24),
                eq("2", h("2h_3h"), b("6s_Ah_Kd_5c"), "turn", 0.16, 0.22),
                eq("3", h("Ah_Kd"), b("Ks_7h_2d"), "flop", 0.56, 0.62),
                eq("4", h("Kh_8d"), b("Ks_7h_2d"), "flop", 0.46, 0.52),
                eq("5 set", h("7s_7h"), b("Kd_7c_2s"), "flop", 0.73, 0.78),
                eq("6 trips", h("Kc_7d"), b("Kh_Ks_2d"), "flop", 0.70, 0.75),
                eq("7 overpair", h("Qd_Qh"), b("Jc_7s_2d"), "flop", 0.60, 0.66),
                eq("8 bottom", h("5c_5d"), b("Ah_Kd_9s"), "flop", 0.28, 0.34),
                eq("9 OESD", h("9h_8d"), b("Tc_7s_2d"), "flop", 0.24, 0.30),
                eq("10 combo", h("Jh_Th"), b("9h_8h_2c"), "flop", 0.32, 0.40),
                eq("11 flush", h("Ah_5h"), b("Kh_9h_2d"), "flop", 0.26, 0.32),
                eq("12 OESD", h("Qc_Jd"), b("Tc_9s_2h"), "flop", 0.24, 0.30),
                eq("13 OESD", h("8s_7s"), b("6h_5d_2c"), "flop", 0.24, 0.30),
                eq("14 gutshot", h("9c_6d"), b("8h_5s_2c"), "flop", 0.18, 0.24),
                eq("15 board", h("Ac_2d"), b("Kh_Ks_Qd"), "flop", 0.12, 0.18),
                eq("16 board turn", h("Kc_2d"), b("Kh_Ks_Qd_7h"), "turn", 0.10, 0.18),
                eq("17 trips", h("Ah_9d"), b("9s_9h_Kd"), "flop", 0.70, 0.76),
                eq("18 two pair", h("Kc_Qd"), b("Ks_Qh_7d"), "flop", 0.60, 0.66),
                eq("19 fake", h("Kc_2d"), b("Ks_Kh_7d_3s"), "turn", 0.38, 0.48),
                eq("20 two pair", h("Ad_Kc"), b("Ah_7d_7s"), "flop", 0.60, 0.66),
                eq("21 combo", h("9h_8h"), b("7h_6h_Kd"), "flop", 0.32, 0.40),
                eq("22 combo", h("Jc_Tc"), b("9c_8c_2d"), "flop", 0.32, 0.40),
                eq("23 combo", h("Qh_Jh"), b("Th_9h_2c"), "flop", 0.32, 0.40),
                eq("24 set", h("6c_6d"), b("6h_3s_2d"), "flop", 0.73, 0.78),
                eq("25 rocket", h("Ah_Kh"), b("Qh_Jh_Th"), "flop", 0.90, 0.93),
                eq("26 boat", h("5d_5c"), b("5h_Ks_Kd"), "flop", 0.84, 0.88),
                eq("27 quads", h("As_Ad"), b("Ac_Ah_Kd"), "turn", 0.88, 0.92),
                eq("28 combo", h("9s_8s"), b("7s_6s_Kd"), "flop", 0.32, 0.40),
                eq("29 high", h("2c_7d"), b("Ah_Kd_Qs"), "flop", 0.12, 0.16),
                eq("30 OESD", h("Jd_Td"), b("9c_8s_2h"), "flop", 0.24, 0.30)
        );
    }

    private static Arguments eq(String label, List<String> hole, List<String> board,
                                String stage, double min, double max) {
        return Arguments.of(label, hole, board, stage, min, max);
    }

    private static List<String> h(String... cards) {
        return NpcEvalTestSupport.hole(cards);
    }

    private static List<String> b(String... cards) {
        return NpcEvalTestSupport.board(cards);
    }
}
