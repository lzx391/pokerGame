package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;

import java.util.List;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.getRankFromCard;

/**
 * 统一听牌检测（P0）：成花/成顺后无听牌；优先级 COMBO &gt; FLUSH &gt; OESD &gt; GUTSHOT。
 */
public final class DpDrawDetector {

    private DpDrawDetector() {
    }

    public static DpNpcDrawCategory detect(
            HandStrength hs,
            List<String> hole,
            List<String> community,
            String stage) {
        if ("river".equals(stage)) {
            return DpNpcDrawCategory.NONE;
        }
        int cat = hs != null ? hs.rankCategory : 0;
        if (cat >= 5) {
            return DpNpcDrawCategory.NONE;
        }
        boolean flushDraw = hasFlushDraw(hs, hole, community);
        DpNpcDrawCategory straightDraw = detectStraightDraw(hole, community);
        if (flushDraw && straightDraw != DpNpcDrawCategory.NONE) {
            return DpNpcDrawCategory.COMBO_DRAW;
        }
        if (flushDraw) {
            return DpNpcDrawCategory.FLUSH_DRAW;
        }
        return straightDraw;
    }

    public static boolean hasFlushDraw(HandStrength hs, List<String> hole, List<String> community) {
        if (hole == null || community == null || hole.isEmpty()) {
            return false;
        }
        if (hs != null && hs.rankCategory >= 6) {
            return false;
        }
        int[] suitCount = new int[4];
        int[] suitHoleCount = new int[4];
        parseSuitCounts(hole, community, suitCount, suitHoleCount);
        for (int s = 0; s < 4; s++) {
            if (suitCount[s] >= 4 && suitHoleCount[s] >= 1) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public static boolean hasStrongFlushDraw(List<String> hole, List<String> community) {
        return hasFlushDraw(null, hole, community);
    }

    @Deprecated
    public static boolean hasStrongStraightDraw(List<String> hole, List<String> community) {
        DpNpcDrawCategory d = detectStraightDraw(hole, community);
        return d == DpNpcDrawCategory.OESD || d == DpNpcDrawCategory.COMBO_DRAW;
    }

    @Deprecated
    public static boolean hasOpenEndedStraightDraw(List<String> hole, List<String> community) {
        return detectStraightDraw(hole, community) == DpNpcDrawCategory.OESD;
    }

    private static DpNpcDrawCategory detectStraightDraw(List<String> hole, List<String> community) {
        if (hole == null || community == null) {
            return DpNpcDrawCategory.NONE;
        }
        RankPresence presence = buildRankPresence(hole, community);
        if (!presence.holeUsed) {
            return DpNpcDrawCategory.NONE;
        }

        DpNpcDrawCategory best = DpNpcDrawCategory.NONE;

        for (int start = 1; start <= 10; start++) {
            int consecutive = 0;
            boolean holeIn = false;
            for (int r = start; r <= start + 3; r++) {
                if (presence.hasRank(r)) {
                    consecutive++;
                    if (presence.holeHasRank(r)) {
                        holeIn = true;
                    }
                }
            }
            if (consecutive != 4 || !holeIn) {
                continue;
            }
            boolean lowOpen = canExtendStraightLow(start, presence);
            boolean highOpen = canExtendStraightHigh(start + 3, presence);
            if (lowOpen && highOpen) {
                return DpNpcDrawCategory.OESD;
            }
            if (lowOpen || highOpen) {
                best = maxDraw(best, DpNpcDrawCategory.GUTSHOT);
            }
        }

        DpNpcDrawCategory outDraw = detectStraightDrawByOuts(presence);
        if (outDraw != DpNpcDrawCategory.NONE) {
            best = maxDraw(best, outDraw);
        }

        for (int top = 5; top <= 14; top++) {
            int[] straight = straightRanksForTop(top);
            int present = 0;
            int missing = -1;
            boolean holeIn = false;
            for (int r : straight) {
                if (presence.hasRank(r)) {
                    present++;
                    if (presence.holeHasRank(r)) {
                        holeIn = true;
                    }
                } else if (missing < 0) {
                    missing = r;
                } else {
                    present = -1;
                    break;
                }
            }
            if (!holeIn || present != 4 || missing < 0) {
                continue;
            }
            boolean missingLow = missing == straight[0];
            boolean missingHigh = missing == straight[4];
            if (missingLow || missingHigh) {
                int start = straight[0];
                int end = straight[4];
                boolean lowOpen = missingLow || canExtendStraightLow(start, presence);
                boolean highOpen = missingHigh || canExtendStraightHigh(end, presence);
                if (lowOpen && highOpen) {
                    return DpNpcDrawCategory.OESD;
                }
                best = maxDraw(best, DpNpcDrawCategory.GUTSHOT);
            } else {
                best = maxDraw(best, DpNpcDrawCategory.GUTSHOT);
            }
        }
        return best;
    }

    /** 枚举下一张补牌是否形成 4+ outs 顺听（覆盖 A23 等 wheel 内听） */
    private static DpNpcDrawCategory detectStraightDrawByOuts(RankPresence presence) {
        int oesdOuts = 0;
        int gutshotOuts = 0;
        for (int out = 2; out <= 14; out++) {
            RankPresence aug = presence.withRank(out);
            DpNpcDrawCategory d = detectStraightDrawWindowOnly(aug);
            if (d == DpNpcDrawCategory.OESD) {
                oesdOuts++;
            } else if (d == DpNpcDrawCategory.GUTSHOT) {
                gutshotOuts++;
            }
        }
        if (oesdOuts >= 2) {
            return DpNpcDrawCategory.OESD;
        }
        if (gutshotOuts >= 1) {
            return DpNpcDrawCategory.GUTSHOT;
        }
        return DpNpcDrawCategory.NONE;
    }

    private static DpNpcDrawCategory detectStraightDrawWindowOnly(RankPresence presence) {
        for (int start = 1; start <= 10; start++) {
            int consecutive = 0;
            boolean holeIn = false;
            for (int r = start; r <= start + 3; r++) {
                if (presence.hasRank(r)) {
                    consecutive++;
                    if (presence.holeHasRank(r)) {
                        holeIn = true;
                    }
                }
            }
            if (consecutive == 4 && holeIn) {
                boolean lowOpen = canExtendStraightLow(start, presence);
                boolean highOpen = canExtendStraightHigh(start + 3, presence);
                if (lowOpen && highOpen) {
                    return DpNpcDrawCategory.OESD;
                }
                if (lowOpen || highOpen) {
                    return DpNpcDrawCategory.GUTSHOT;
                }
            }
        }
        return DpNpcDrawCategory.NONE;
    }

    private static DpNpcDrawCategory maxDraw(DpNpcDrawCategory a, DpNpcDrawCategory b) {
        if (a == null || a == DpNpcDrawCategory.NONE) {
            return b;
        }
        if (b == null || b == DpNpcDrawCategory.NONE) {
            return a;
        }
        return a.drawStrengthOrder() >= b.drawStrengthOrder() ? a : b;
    }

    private static int[] straightRanksForTop(int top) {
        if (top == 5) {
            return new int[]{1, 2, 3, 4, 5};
        }
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            out[i] = top - 4 + i;
        }
        return out;
    }

    private static boolean canExtendStraightLow(int start, RankPresence presence) {
        if (start <= 1) {
            return false;
        }
        int below = start - 1;
        return !presence.hasRank(below);
    }

    private static boolean canExtendStraightHigh(int end, RankPresence presence) {
        if (end >= 14) {
            return false;
        }
        return !presence.hasRank(end + 1);
    }

    private static void parseSuitCounts(
            List<String> hole,
            List<String> community,
            int[] suitCount,
            int[] suitHoleCount) {
        if (hole != null) {
            for (String c : hole) {
                int code = suitCode(c);
                if (code >= 0 && code < 4) {
                    suitCount[code]++;
                    suitHoleCount[code]++;
                }
            }
        }
        if (community != null) {
            for (String c : community) {
                int code = suitCode(c);
                if (code >= 0 && code < 4) {
                    suitCount[code]++;
                }
            }
        }
    }

    private static int suitCode(String card) {
        if (card == null || !card.contains("_")) {
            return -1;
        }
        return switch (card.split("_", 2)[0]) {
            case "hearts" -> 0;
            case "diamonds" -> 1;
            case "clubs" -> 2;
            case "spades" -> 3;
            default -> -1;
        };
    }

    private static RankPresence buildRankPresence(List<String> hole, List<String> community) {
        boolean[] seen = new boolean[15];
        boolean[] holeSeen = new boolean[15];
        boolean holeUsed = false;
        if (hole != null) {
            for (String c : hole) {
                int r = getRankFromCard(c);
                if (r > 0) {
                    seen[r] = true;
                    holeSeen[r] = true;
                    holeUsed = true;
                    if (r == 14) {
                        seen[1] = true;
                        holeSeen[1] = true;
                    }
                }
            }
        }
        if (community != null) {
            for (String c : community) {
                int r = getRankFromCard(c);
                if (r > 0) {
                    seen[r] = true;
                    if (r == 14) {
                        seen[1] = true;
                    }
                }
            }
        }
        return new RankPresence(seen, holeSeen, holeUsed);
    }

    private static final class RankPresence {
        final boolean[] seen;
        final boolean[] holeSeen;
        final boolean holeUsed;

        RankPresence(boolean[] seen, boolean[] holeSeen, boolean holeUsed) {
            this.seen = seen;
            this.holeSeen = holeSeen;
            this.holeUsed = holeUsed;
        }

        boolean hasRank(int r) {
            return r >= 1 && r <= 14 && seen[r];
        }

        boolean holeHasRank(int r) {
            return r >= 1 && r <= 14 && holeSeen[r];
        }

        RankPresence withRank(int rank) {
            boolean[] s = seen.clone();
            boolean[] h = holeSeen.clone();
            s[rank] = true;
            if (rank == 14) {
                s[1] = true;
            }
            return new RankPresence(s, h, holeUsed);
        }
    }
}
