package com.example.mgdemoplus.npc.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpBoardTextureTest {

    @Test
    void dryUnpairedRainbowBoard() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("Ks_7h_2d"));
        assertTrue(tex.isDry());
        assertFalse(tex.wet);
        assertFalse(tex.paired);
        assertFalse(tex.monotone);
    }

    @Test
    void monotoneFlopIsWet() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("Qh_Jh_9h"));
        assertTrue(tex.monotone);
        assertTrue(tex.wet);
        assertTrue(tex.dangerousForTopPair());
    }

    @Test
    void pairedBoardDetected() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("Kh_Ks_Qd"));
        assertTrue(tex.paired);
        assertFalse(tex.doublePaired);
    }

    @Test
    void doublePairedTurnBoard() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("Kh_Ks_Qd_Qh"));
        assertTrue(tex.doublePaired);
        assertTrue(tex.dangerousForTwoPairPlus());
    }

    @Test
    void fourFlushBoard() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("Ah_Kh_9h_2h"));
        assertTrue(tex.fourFlush);
        assertTrue(tex.wet);
        assertTrue(tex.dangerousForTwoPairPlus());
    }

    @Test
    void straightPossibleConnectedBoard() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("Tc_9s_8d"));
        assertTrue(tex.straightPossible);
        assertTrue(tex.wet);
    }

    @Test
    void highCardBoardFlag() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("Ah_Kd_Qs"));
        assertTrue(tex.highCardBoard);
        assertFalse(tex.lowCardBoard);
    }

    @Test
    void lowCardBoardFlag() {
        DpBoardTexture tex = DpBoardTexture.analyze(NpcEvalTestSupport.board("8h_5d_2c"));
        assertTrue(tex.lowCardBoard);
    }

    @Test
    void snapshotCarriesBoardTexture() {
        List<String> hole = NpcEvalTestSupport.hole("Kh_8d");
        List<String> board = NpcEvalTestSupport.board("Ks_7h_2d");
        DpNpcHandSnapshot snap = DpNpcHandClassifier.classifyPostflop(
                com.example.mgdemoplus.utils.DpUtilHandEvaluator.evaluateBestHand(
                        List.of(hole.get(0), hole.get(1), board.get(0), board.get(1), board.get(2))),
                hole, board, "flop");
        assertTrue(snap.boardTexture != null);
        assertTrue(snap.boardTexture.isDry());
    }
}
