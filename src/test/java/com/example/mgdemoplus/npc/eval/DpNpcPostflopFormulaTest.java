package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcPostflopFormulaTest {

    @Test
    void tptkDryBoardHigherCbetThanTpwkWet() {
        DpBoardTexture dry = DpBoardTexture.analyze(NpcEvalTestSupport.board("Ks_7h_2d"));
        DpBoardTexture wet = DpBoardTexture.analyze(NpcEvalTestSupport.board("Qh_Jh_9h"));
        double tptkDry = DpNpcPostflopFormula.cbetPotFactor(
                DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER, dry, "flop");
        double tpwkWet = DpNpcPostflopFormula.cbetPotFactor(
                DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER, wet, "flop");
        assertTrue(tptkDry > tpwkWet);
    }

    @Test
    void comboDrawRaisesSemiBluffProb() {
        double highOnly = DpNpcPostflopFormula.semiBluffProb(
                DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.GUTSHOT, 1.0);
        double combo = DpNpcPostflopFormula.semiBluffProb(
                DpNpcMadeHandCategory.HIGH_CARD, DpNpcDrawCategory.COMBO_DRAW, 1.0);
        assertTrue(combo > highOnly);
    }

    @Test
    void flushDrawReducesFoldForCallStations() {
        double reduction = DpNpcPostflopFormula.drawCallFoldReduction(
                DpNpcDrawCategory.FLUSH_DRAW, 0.25);
        assertTrue(reduction > 0.1);
    }

    @Test
    void monsterHigherValueBetThanMiddlePair() {
        DpBoardTexture dry = DpBoardTexture.analyze(NpcEvalTestSupport.board("Ks_7h_2d"));
        double monster = DpNpcPostflopFormula.valueBetProb(
                DpNpcMadeHandCategory.FULL_HOUSE, dry, "flop", HandPlanType.VALUE);
        double mid = DpNpcPostflopFormula.valueBetProb(
                DpNpcMadeHandCategory.MIDDLE_PAIR, dry, "flop", HandPlanType.POT_CONTROL);
        assertTrue(monster > mid);
    }

    @Test
    void wetBoardIncreasesFoldForTpwk() {
        DpBoardTexture wet = DpBoardTexture.analyze(NpcEvalTestSupport.board("Qh_Jh_9h"));
        double foldWet = DpNpcPostflopFormula.baseFoldProb(
                DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER,
                DpNpcDrawCategory.NONE,
                wet,
                0.65,
                BoardDanger.WET,
                1.0);
        double foldDry = DpNpcPostflopFormula.baseFoldProb(
                DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER,
                DpNpcDrawCategory.NONE,
                DpBoardTexture.analyze(NpcEvalTestSupport.board("Ks_7h_2d")),
                0.65,
                BoardDanger.DRY,
                1.0);
        assertTrue(foldWet >= foldDry);
    }
}
