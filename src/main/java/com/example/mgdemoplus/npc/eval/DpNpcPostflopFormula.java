package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.RuleNpcConfig;

/**
 * 12 档成牌 + 听牌 + 牌面风险的公式化翻后决策辅助（各 Strategy 乘人格系数）。
 */
public final class DpNpcPostflopFormula {

    private DpNpcPostflopFormula() {
    }

    public static DpNpcMadeHandCategory madeOrHigh(DpNpcHandSnapshot snap) {
        if (snap == null || snap.made == null) {
            return DpNpcMadeHandCategory.HIGH_CARD;
        }
        return snap.made;
    }

    public static DpNpcDrawCategory drawOrNone(DpNpcHandSnapshot snap) {
        if (snap == null || snap.draw == null) {
            return DpNpcDrawCategory.NONE;
        }
        return snap.draw;
    }

    public static DpBoardTexture textureOrDry(DpNpcHandSnapshot snap) {
        if (snap != null && snap.boardTexture != null) {
            return snap.boardTexture;
        }
        return DpBoardTexture.analyze(null);
    }

    public static boolean isGranularPostflop(DpNpcHandSnapshot snap) {
        return snap != null && snap.made != null;
    }

    public static boolean isMonster(DpNpcMadeHandCategory made) {
        return made.isAtLeast(DpNpcMadeHandCategory.FULL_HOUSE);
    }

    public static boolean isStrongValue(DpNpcMadeHandCategory made) {
        return made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR);
    }

    public static boolean isTopPairPlus(DpNpcMadeHandCategory made) {
        return made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER);
    }

    public static boolean isMarginalPair(DpNpcMadeHandCategory made) {
        return made == DpNpcMadeHandCategory.BOTTOM_PAIR
                || made == DpNpcMadeHandCategory.MIDDLE_PAIR
                || made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER;
    }

    public static boolean hasSemiBluffDraw(DpNpcDrawCategory draw) {
        return draw.isAtLeast(DpNpcDrawCategory.OESD);
    }

    public static boolean hasStrongDraw(DpNpcDrawCategory draw) {
        return draw.isAtLeast(DpNpcDrawCategory.FLUSH_DRAW);
    }

    /** 按 12 档映射 c-bet / value 基准 pot 比例 */
    public static double cbetPotFactor(DpNpcMadeHandCategory made, DpBoardTexture tex, String stage) {
        double base;
        if (made.isAtLeast(DpNpcMadeHandCategory.FULL_HOUSE)) {
            base = RuleNpcConfig.CBET_BASE_STRONG + 0.06;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.STRAIGHT)) {
            base = RuleNpcConfig.CBET_BASE_STRONG;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            base = RuleNpcConfig.CBET_BASE_STRONG - 0.04;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            base = RuleNpcConfig.CBET_BASE_MEDIUM + 0.08;
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            base = RuleNpcConfig.CBET_BASE_MEDIUM + 0.04;
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER) {
            base = RuleNpcConfig.CBET_BASE_MEDIUM - 0.12;
        } else if (made == DpNpcMadeHandCategory.MIDDLE_PAIR) {
            base = RuleNpcConfig.CBET_BASE_WEAK + 0.06;
        } else if (made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            base = RuleNpcConfig.CBET_BASE_WEAK;
        } else {
            base = RuleNpcConfig.CBET_BASE_WEAK - 0.08;
        }
        if (tex.wet) {
            base *= 0.88;
        }
        if (tex.dangerousForTopPair() && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            base *= 0.82;
        }
        if ("river".equals(stage)) {
            base *= 1.05;
        }
        return Math.max(0.22, Math.min(0.95, base));
    }

    public static double commitFactor(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            BoardDanger bd) {
        double f;
        if (isMonster(made)) {
            f = 0.88;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.STRAIGHT)) {
            f = 0.78;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            f = 0.72;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            f = tex.dangerousForTwoPairPlus() ? 0.58 : 0.66;
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            f = tex.dangerousForTopPair() ? 0.48 : 0.62;
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER) {
            f = tex.dangerousForTopPair() ? 0.32 : 0.46;
        } else if (made == DpNpcMadeHandCategory.MIDDLE_PAIR) {
            f = 0.38;
        } else if (made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            f = 0.28;
        } else if (hasStrongDraw(draw)) {
            f = 0.42;
        } else if (draw == DpNpcDrawCategory.OESD) {
            f = 0.34;
        } else {
            f = 0.22;
        }
        if (bd == BoardDanger.WET || tex.wet) {
            f *= 0.88;
        }
        return Math.max(0.12, Math.min(0.92, f));
    }

    public static double baseFoldProb(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            double callRatio,
            BoardDanger bd,
            double foldTightnessMul) {
        double base = 0.0;
        if (made == DpNpcMadeHandCategory.HIGH_CARD && !hasSemiBluffDraw(draw)) {
            if (callRatio > 0.5) {
                base = (bd == BoardDanger.WET || tex.wet) ? 0.78 : 0.58;
            } else if (bd == BoardDanger.WET || tex.wet) {
                base = 0.42;
            }
        } else if (made == DpNpcMadeHandCategory.HIGH_CARD && hasSemiBluffDraw(draw)) {
            if (callRatio > 0.65) {
                base = 0.52;
            } else if (callRatio > 0.45) {
                base = 0.28;
            }
        } else if (made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            if (callRatio > 0.45) {
                base = 0.55;
            }
        } else if (made == DpNpcMadeHandCategory.MIDDLE_PAIR) {
            if (callRatio > 0.55 && (bd == BoardDanger.WET || tex.wet)) {
                base = 0.38;
            } else if (callRatio > 0.65) {
                base = 0.28;
            }
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER) {
            if (callRatio > 0.6 && tex.dangerousForTopPair()) {
                base = 0.42;
            } else if (callRatio > 0.7) {
                base = 0.22;
            }
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            if (callRatio > 0.75 && tex.dangerousForTopPair()) {
                base = 0.18;
            }
        }
        return Math.min(1.0, base * foldTightnessMul);
    }

    /** 听牌跟注站：降低弃牌概率 */
    public static double drawCallFoldReduction(DpNpcDrawCategory draw, double potOdds) {
        if (draw == DpNpcDrawCategory.COMBO_DRAW) {
            return potOdds > 0 && potOdds < 0.38 ? 0.28 : 0.18;
        }
        if (draw == DpNpcDrawCategory.FLUSH_DRAW) {
            return potOdds > 0 && potOdds < 0.32 ? 0.22 : 0.12;
        }
        if (draw == DpNpcDrawCategory.OESD) {
            return potOdds > 0 && potOdds < 0.28 ? 0.14 : 0.08;
        }
        if (draw == DpNpcDrawCategory.GUTSHOT) {
            return potOdds > 0 && potOdds < 0.18 ? 0.06 : 0.0;
        }
        return 0.0;
    }

    public static double valueBetProb(
            DpNpcMadeHandCategory made,
            DpBoardTexture tex,
            String stage,
            HandPlanType plan) {
        double prob;
        if (isMonster(made)) {
            prob = 0.88;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.STRAIGHT)) {
            prob = tex.dangerousForTwoPairPlus() ? 0.72 : 0.82;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = 0.78;
        } else if (made == DpNpcMadeHandCategory.TWO_PAIR) {
            prob = tex.dangerousForTwoPairPlus() ? 0.58 : 0.72;
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            prob = tex.isDry() ? 0.68 : (tex.dangerousForTopPair() ? 0.38 : 0.55);
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER) {
            prob = "river".equals(stage) ? 0.12 : (tex.wet ? 0.18 : 0.32);
        } else if (made == DpNpcMadeHandCategory.MIDDLE_PAIR) {
            prob = "river".equals(stage) ? 0.22 : 0.28;
        } else {
            return 0.0;
        }
        if (plan == HandPlanType.VALUE) {
            prob *= 1.12;
        } else if (plan == HandPlanType.POT_CONTROL) {
            prob *= 0.78;
        } else if (plan == HandPlanType.GIVE_UP) {
            prob *= 0.35;
        }
        return Math.min(0.95, Math.max(0.05, prob));
    }

    public static double raiseProb(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan) {
        double prob;
        if (isMonster(made)) {
            prob = 0.82;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.STRAIGHT)) {
            prob = 0.72;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = 0.65;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            prob = 0.55;
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            prob = tex.dangerousForTopPair() ? 0.38 : 0.52;
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER) {
            prob = 0.28;
        } else if (made == DpNpcMadeHandCategory.MIDDLE_PAIR) {
            prob = 0.22;
        } else if (hasSemiBluffDraw(draw)) {
            prob = draw == DpNpcDrawCategory.COMBO_DRAW ? 0.48 : 0.32;
        } else if (draw == DpNpcDrawCategory.GUTSHOT) {
            prob = 0.12;
        } else {
            prob = 0.08;
        }
        if (plan == HandPlanType.BLUFF && (made == DpNpcMadeHandCategory.HIGH_CARD || isMarginalPair(made))) {
            prob *= 1.15;
        } else if (plan == HandPlanType.POT_CONTROL) {
            prob *= 0.78;
        }
        return Math.min(0.92, Math.max(0.05, prob));
    }

    public static double semiBluffProb(DpNpcMadeHandCategory made, DpNpcDrawCategory draw, double bluffFreqMul) {
        if (made != DpNpcMadeHandCategory.HIGH_CARD && !isMarginalPair(made)) {
            return 0.0;
        }
        double base;
        if (draw == DpNpcDrawCategory.COMBO_DRAW) {
            base = 0.42;
        } else if (draw == DpNpcDrawCategory.FLUSH_DRAW) {
            base = 0.28;
        } else if (draw == DpNpcDrawCategory.OESD) {
            base = 0.22;
        } else if (draw == DpNpcDrawCategory.GUTSHOT) {
            base = 0.10;
        } else {
            base = 0.08;
        }
        return Math.min(0.72, base * bluffFreqMul);
    }

    public static int raiseExtraMinBb(DpNpcMadeHandCategory made) {
        if (isMonster(made)) {
            return 4;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            return 4;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return 3;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            return 3;
        }
        return 2;
    }

    public static int raiseExtraMaxBb(DpNpcMadeHandCategory made, String stage) {
        if (isMonster(made)) {
            return "river".equals(stage) ? 8 : 6;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            return "river".equals(stage) ? 7 : 6;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return "river".equals(stage) ? 6 : 5;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            return "river".equals(stage) ? 5 : 4;
        }
        return 4;
    }
}
