package com.example.mgdemoplus.npc.eval;

import java.util.List;
import java.util.Objects;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.getRankFromCard;

/**
 * 12 档成牌 + 听牌轴 → 粗桶 {@code equityEst}（非 MC）。
 */
public final class DpNpcEquityEstimator {

    private DpNpcEquityEstimator() {
    }

    public static double estimate(DpNpcHandSnapshot snap, String stage, List<String> hole) {
        if (snap == null) {
            return clampEquityEstimate(0.25);
        }
        if ("preflop".equals(stage) || snap.preflop != null) {
            return estimatePreflop(snap.preflop, hole);
        }
        return estimatePostflop(snap, stage);
    }

    public static double clampEquityEstimate(double v) {
        if (v < 0.06) {
            return 0.06;
        }
        if (v > 0.93) {
            return 0.93;
        }
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double estimatePreflop(DpNpcPreflopCategory cat, List<String> hole) {
        if (cat == null) {
            cat = DpNpcPreflopCategory.TRASH;
        }
        double base = switch (cat) {
            case PREMIUM -> 0.75;
            case STRONG -> 0.60;
            case PLAYABLE -> 0.40;
            case SPECULATIVE -> 0.32;
            case MARGINAL -> 0.28;
            case TRASH -> 0.25;
        };
        base = applyPreflopHoleEquityAdjustments(base, hole);
        return clampEquityEstimate(base);
    }

    private static double estimatePostflop(DpNpcHandSnapshot snap, String stage) {
        DpNpcMadeHandCategory made = snap.made != null ? snap.made : DpNpcMadeHandCategory.HIGH_CARD;
        double base = madeBase(made, stage);
        base = applyDrawBonus(base, snap.draw, stage);
        if (snap.playingTheBoard) {
            base = Math.min(base, 0.22);
        }
        if (snap.counterfeit) {
            base *= 0.85;
        }
        if (snap.pocketSet && made == DpNpcMadeHandCategory.TRIPS) {
            base += 0.03;
        }
        if (snap.overpair && made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            base += 0.05;
        }
        return clampEquityEstimate(base);
    }

    private static double madeBase(DpNpcMadeHandCategory made, String stage) {
        boolean turn = "turn".equals(stage);
        boolean river = "river".equals(stage);
        return switch (made) {
            case HIGH_CARD -> river ? 0.10 : (turn ? 0.12 : 0.14);
            case BOTTOM_PAIR -> river ? 0.26 : (turn ? 0.28 : 0.30);
            case MIDDLE_PAIR -> river ? 0.34 : (turn ? 0.36 : 0.38);
            case TOP_PAIR_WEAK_KICKER -> river ? 0.44 : (turn ? 0.46 : 0.48);
            case TOP_PAIR_TOP_KICKER -> river ? 0.54 : (turn ? 0.56 : 0.58);
            case TWO_PAIR -> river ? 0.58 : (turn ? 0.60 : 0.62);
            case TRIPS -> river ? 0.68 : (turn ? 0.70 : 0.72);
            case STRAIGHT -> river ? 0.74 : (turn ? 0.76 : 0.78);
            case FLUSH -> river ? 0.76 : (turn ? 0.78 : 0.80);
            case FULL_HOUSE -> river ? 0.84 : (turn ? 0.85 : 0.86);
            case QUADS -> river ? 0.88 : (turn ? 0.89 : 0.90);
            case ROCKET -> river ? 0.90 : (turn ? 0.91 : 0.92);
        };
    }

    private static double applyDrawBonus(double base, DpNpcDrawCategory draw, String stage) {
        if (draw == null || draw == DpNpcDrawCategory.NONE || "river".equals(stage)) {
            return base;
        }
        boolean turn = "turn".equals(stage);
        double delta = switch (draw) {
            case GUTSHOT -> turn ? 0.04 : 0.06;
            case OESD -> turn ? 0.08 : 0.12;
            case FLUSH_DRAW -> turn ? 0.10 : 0.14;
            case COMBO_DRAW -> turn ? 0.15 : 0.22;
            default -> 0.0;
        };
        double factor = 1.0;
        if (base > 0.70) {
            factor = 0.0;
        } else if (base > 0.55) {
            factor = 0.7;
        }
        return base + delta * factor;
    }

    /** 翻前细调（自 {@link com.example.mgdemoplus.npc.engine.DpNpcEngine} 迁入，逻辑不变） */
    static double applyPreflopHoleEquityAdjustments(double base, List<String> hole) {
        if (hole == null || hole.size() < 2) {
            return base;
        }
        int r1 = getRankFromCard(hole.get(0));
        int r2 = getRankFromCard(hole.get(1));
        if (r1 <= 0 || r2 <= 0) {
            return base;
        }
        boolean suited = holeSuitsEqual(hole.get(0), hole.get(1));
        int hi = Math.max(r1, r2);
        int lo = Math.min(r1, r2);
        int gap = hi - lo;
        double b = base;
        if (gap == 0) {
            if (hi <= 6) {
                b = Math.max(b, 0.37);
            } else if (hi <= 8) {
                b = Math.max(b, 0.39);
            }
            return b;
        }
        if (suited) {
            if (gap <= 2 && hi >= 5 && hi <= 12 && lo >= 4) {
                b += 0.045;
            }
            if (hi == 14 && lo >= 10) {
                b += 0.04;
            } else if (hi == 14 && lo >= 5 && lo <= 9) {
                b += 0.03;
            }
        } else {
            if (hi >= 12 && lo >= 10 && gap <= 2) {
                b += 0.035;
            }
            if (hi == 14 && lo >= 11) {
                b += 0.03;
            }
        }
        return b;
    }

    private static boolean holeSuitsEqual(String c1, String c2) {
        if (c1 == null || c2 == null || !c1.contains("_") || !c2.contains("_")) {
            return false;
        }
        return Objects.equals(c1.split("_", 2)[0], c2.split("_", 2)[0]);
    }
}
