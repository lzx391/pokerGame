package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.utils.DpUtilHandEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpDrawDetectorTest {

    @Test
    void flushDrawRequiresHeroCard() {
        List<String> hole = NpcEvalTestSupport.hole("Ah_5h");
        List<String> board = NpcEvalTestSupport.board("Kh_9h_2d");
        assertEquals(DpNpcDrawCategory.FLUSH_DRAW,
                DpDrawDetector.detect(null, hole, board, "flop"));
    }

    @Test
    void comboDrawDetected() {
        List<String> hole = NpcEvalTestSupport.hole("Jh_Th");
        List<String> board = NpcEvalTestSupport.board("9h_8h_2c");
        assertEquals(DpNpcDrawCategory.COMBO_DRAW,
                DpDrawDetector.detect(null, hole, board, "flop"));
    }

    @Test
    void riverIsAlwaysNone() {
        List<String> hole = NpcEvalTestSupport.hole("Jh_Th");
        List<String> board = NpcEvalTestSupport.board("9h_8h_2c");
        assertEquals(DpNpcDrawCategory.NONE,
                DpDrawDetector.detect(null, hole, board, "river"));
    }

    @Test
    void madeStraightHasNoDraw() {
        List<String> hole = NpcEvalTestSupport.hole("9h_8d");
        List<String> board = NpcEvalTestSupport.board("7c_6s_5h");
        var all = new java.util.ArrayList<>(hole);
        all.addAll(board);
        var hs = DpUtilHandEvaluator.evaluateBestHand(all);
        assertEquals(DpNpcDrawCategory.NONE,
                DpDrawDetector.detect(hs, hole, board, "flop"));
    }

    @Test
    void evaluatorDelegatesFlushDrawConsistently() {
        List<String> hole = NpcEvalTestSupport.hole("Ah_5h");
        List<String> board = NpcEvalTestSupport.board("Kh_9h_2d");
        assertTrue(DpUtilHandEvaluator.hasStrongFlushDraw(hole, board));
        assertFalse(DpUtilHandEvaluator.hasStrongFlushDraw(
                NpcEvalTestSupport.hole("Ac_5d"), board));
    }
}
