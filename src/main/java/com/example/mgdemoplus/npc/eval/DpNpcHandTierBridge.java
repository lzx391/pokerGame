package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;

/**
 * 12 档快照 → 旧四档兼容桥（Strategy 过渡 / feature flag 回滚 / LLM 旁路）。
 */
public final class DpNpcHandTierBridge {

    private DpNpcHandTierBridge() {
    }

    public static SimpleStrength toSimpleStrength(DpNpcHandSnapshot snap) {
        if (snap == null) {
            return SimpleStrength.WEAK;
        }
        if (snap.preflop != null) {
            return preflopToSimple(snap.preflop);
        }
        DpNpcMadeHandCategory made = snap.made != null ? snap.made : DpNpcMadeHandCategory.HIGH_CARD;
        DpNpcDrawCategory draw = snap.draw != null ? snap.draw : DpNpcDrawCategory.NONE;

        if (made.isAtLeast(DpNpcMadeHandCategory.FULL_HOUSE)) {
            return SimpleStrength.MONSTER;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.STRAIGHT)) {
            return SimpleStrength.STRONG;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return SimpleStrength.MEDIUM;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            return SimpleStrength.MEDIUM;
        }
        if (draw == DpNpcDrawCategory.COMBO_DRAW) {
            return SimpleStrength.STRONG;
        }
        if (draw.isAtLeast(DpNpcDrawCategory.FLUSH_DRAW) || draw == DpNpcDrawCategory.OESD) {
            return SimpleStrength.MEDIUM;
        }
        return SimpleStrength.WEAK;
    }

    private static SimpleStrength preflopToSimple(DpNpcPreflopCategory cat) {
        return switch (cat) {
            case PREMIUM -> SimpleStrength.STRONG;
            case STRONG -> SimpleStrength.STRONG;
            case PLAYABLE -> SimpleStrength.MEDIUM;
            case SPECULATIVE -> SimpleStrength.MEDIUM;
            case MARGINAL -> SimpleStrength.WEAK;
            case TRASH -> SimpleStrength.WEAK;
        };
    }
}
