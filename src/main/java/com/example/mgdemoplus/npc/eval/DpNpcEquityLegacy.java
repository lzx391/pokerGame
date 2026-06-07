package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;

import java.util.List;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.getRankFromCard;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.isPlayingBoardPairOnly;

/**
 * 旧四档 {@link SimpleStrength} 权益桶（feature flag=false 回退路径）。
 */
final class DpNpcEquityLegacy {

    private DpNpcEquityLegacy() {
    }

    static double estimate(
            SimpleStrength st,
            String stage,
            List<String> hole,
            List<String> community,
            HandStrength hsMade) {
        if (st == null) {
            return DpNpcEquityEstimator.clampEquityEstimate(0.25);
        }
        boolean preflop = "preflop".equals(stage);
        double base;
        switch (st) {
            case MONSTER:
                base = preflop ? 0.75 : 0.85;
                break;
            case STRONG:
                base = preflop ? 0.60 : 0.65;
                break;
            case MEDIUM:
                base = preflop ? 0.40 : 0.45;
                break;
            case WEAK:
            default:
                base = preflop ? 0.25 : 0.20;
                break;
        }
        if (preflop) {
            base = DpNpcEquityEstimator.applyPreflopHoleEquityAdjustments(base, hole);
            return DpNpcEquityEstimator.clampEquityEstimate(base);
        }
        if (hsMade != null) {
            base = applyPostflopMadeHandEquityAdjustments(base, st, hsMade, hole, community);
        }
        if ("flop".equals(stage) || "turn".equals(stage)) {
            boolean strongDraw = DpDrawDetector.hasStrongFlushDraw(hole, community)
                    || DpDrawDetector.hasOpenEndedStraightDraw(hole, community);
            if (strongDraw) {
                if (st == SimpleStrength.MEDIUM) {
                    base = Math.max(base, 0.50);
                } else if (st == SimpleStrength.WEAK) {
                    base = Math.max(base, 0.30);
                }
            }
        }
        return DpNpcEquityEstimator.clampEquityEstimate(base);
    }

    private static double applyPostflopMadeHandEquityAdjustments(
            double base,
            SimpleStrength st,
            HandStrength hs,
            List<String> hole,
            List<String> community) {
        if (hs == null || hole == null || community == null) {
            return base;
        }
        int cat = hs.rankCategory;
        List<Integer> rk = hs.ranks;
        int boardHigh = maxRankOnBoard(community);

        if (cat >= 7) {
            return Math.max(base, 0.82);
        }
        if (cat == 6 || cat == 5) {
            return Math.max(base, 0.68);
        }
        if (cat == 4) {
            return Math.max(base, 0.52);
        }
        if (cat == 3) {
            return Math.max(base, 0.44);
        }
        if (cat == 2 && rk != null && !rk.isEmpty()) {
            int pr = rk.get(0);
            if (isPlayingBoardPairOnly(hs, hole, community)) {
                return Math.min(base, 0.19);
            }
            if (pr > boardHigh) {
                return Math.max(base, 0.62);
            }
            if (pr == boardHigh) {
                return Math.max(base, 0.53);
            }
            if (pr >= 10) {
                return Math.max(base, 0.37);
            }
            return Math.max(base, 0.28);
        }
        if (cat == 1 && rk != null && !rk.isEmpty()) {
            int k0 = rk.get(0);
            if (k0 == 14) {
                return Math.max(base, 0.24);
            }
        }
        return base;
    }

    private static int maxRankOnBoard(List<String> community) {
        if (community == null) {
            return 0;
        }
        int m = 0;
        for (String c : community) {
            m = Math.max(m, getRankFromCard(c));
        }
        return m;
    }
}
