package com.example.mgdemoplus.npc.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcHandClassifierTest {

    @ParameterizedTest(name = "#{index} {0}")
    @MethodSource("planCases")
    void classifyMatchesPlan(
            String label,
            List<String> hole,
            List<String> board,
            String stage,
            DpNpcMadeHandCategory expectedMade,
            DpNpcDrawCategory expectedDraw,
            boolean expectPlayingTheBoard,
            boolean expectCounterfeit) {
        DpNpcHandSnapshot snap = DpNpcHandClassifier.classify(hole, board, stage);
        assertEquals(expectedMade, snap.made, label + " made");
        assertEquals(expectedDraw, snap.draw, label + " draw");
        if (expectPlayingTheBoard) {
            assertTrue(snap.playingTheBoard, label + " playingTheBoard");
        }
        if (expectCounterfeit) {
            assertTrue(snap.counterfeit, label + " counterfeit");
        }
    }

    static Stream<Arguments> planCases() {
        return Stream.of(
                Arguments.of("1 gutshot", h("2h_3h"), b("6s_Ah_Kd"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.GUTSHOT, false, false),
                Arguments.of("2 gutshot turn", h("2h_3h"), b("6s_Ah_Kd_5c"), "turn",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.GUTSHOT, false, false),
                Arguments.of("3 TPTK", h("Ah_Kd"), b("Ks_7h_2d"), "flop",
                        DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("4 TPWK", h("Kh_8d"), b("Ks_7h_2d"), "flop",
                        DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("5 set", h("7s_7h"), b("Kd_7c_2s"), "flop",
                        DpNpcMadeHandCategory.TRIPS, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("6 trips", h("Kc_7d"), b("Kh_Ks_2d"), "flop",
                        DpNpcMadeHandCategory.TRIPS, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("7 overpair", h("Qd_Qh"), b("Jc_7s_2d"), "flop",
                        DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("8 bottom pair", h("5c_5d"), b("Ah_Kd_9s"), "flop",
                        DpNpcMadeHandCategory.BOTTOM_PAIR, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("9 OESD high", h("9h_8d"), b("Tc_7s_2d"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.OESD, false, false),
                Arguments.of("10 combo", h("Jh_Th"), b("9h_8h_2c"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.COMBO_DRAW, false, false),
                Arguments.of("11 flush draw", h("Ah_5h"), b("Kh_9h_2d"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.FLUSH_DRAW, false, false),
                Arguments.of("12 OESD", h("Qc_Jd"), b("Tc_9s_2h"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.OESD, false, false),
                Arguments.of("13 OESD", h("8s_7s"), b("6h_5d_2c"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.OESD, false, false),
                Arguments.of("14 gutshot", h("9c_6d"), b("8h_5s_2c"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.GUTSHOT, false, false),
                Arguments.of("15 board pair", h("Ac_2d"), b("Kh_Ks_Qd"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, true, false),
                Arguments.of("16 playing board turn", h("Kc_2d"), b("Kh_Ks_Qd_7h"), "turn",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, true, false),
                Arguments.of("17 trips", h("Ah_9d"), b("9s_9h_Kd"), "flop",
                        DpNpcMadeHandCategory.TRIPS, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("18 two pair", h("Kc_Qd"), b("Ks_Qh_7d"), "flop",
                        DpNpcMadeHandCategory.TWO_PAIR, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("19 fake two pair", h("Kc_2d"), b("Ks_Kh_7d_3s"), "turn",
                        DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER, DpNpcDrawCategory.NONE, false, true),
                Arguments.of("20 two pair", h("Ad_Kc"), b("Ah_7d_7s"), "flop",
                        DpNpcMadeHandCategory.TWO_PAIR, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("21 combo", h("9h_8h"), b("7h_6h_Kd"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.COMBO_DRAW, false, false),
                Arguments.of("22 combo", h("Jc_Tc"), b("9c_8c_2d"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.COMBO_DRAW, false, false),
                Arguments.of("23 combo", h("Qh_Jh"), b("Th_9h_2c"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.COMBO_DRAW, false, false),
                Arguments.of("24 set", h("6c_6d"), b("6h_3s_2d"), "flop",
                        DpNpcMadeHandCategory.TRIPS, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("25 rocket", h("Ah_Kh"), b("Qh_Jh_Th"), "flop",
                        DpNpcMadeHandCategory.ROCKET, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("26 full house", h("5d_5c"), b("5h_Ks_Kd"), "flop",
                        DpNpcMadeHandCategory.FULL_HOUSE, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("27 quads turn", h("As_Ad"), b("Ac_Ah_Kd"), "turn",
                        DpNpcMadeHandCategory.QUADS, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("28 combo", h("9s_8s"), b("7s_6s_Kd"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.COMBO_DRAW, false, false),
                Arguments.of("29 high card", h("2c_7d"), b("Ah_Kd_Qs"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.NONE, false, false),
                Arguments.of("30 OESD", h("Jd_Td"), b("9c_8s_2h"), "flop",
                        DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.OESD, false, false)
        );
    }

    @Test
    void riverHasNoDraw() {
        DpNpcHandSnapshot snap = DpNpcHandClassifier.classify(
                h("Jh_Th"), b("9h_8h_2c_3d_4s"), "river");
        assertEquals(DpNpcDrawCategory.NONE, snap.draw);
    }

    private static List<String> h(String... cards) {
        return NpcEvalTestSupport.hole(cards);
    }

    private static List<String> b(String... cards) {
        return NpcEvalTestSupport.board(cards);
    }
}
