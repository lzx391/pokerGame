package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.evaluateBestHand;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.getBestHandCards;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.getRankFromCard;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.isPlayingBoardPairOnly;

/**
 * 翻后 12 档成牌分类 + 听牌轴；翻前映射 {@link DpNpcPreflopCategory}。
 */
public final class DpNpcHandClassifier {

    private DpNpcHandClassifier() {
    }

    public static DpNpcHandSnapshot classify(
            List<String> hole,
            List<String> community,
            String stage) {
        if ("preflop".equals(stage) || community == null || community.size() < 3) {
            return DpNpcHandSnapshot.preflop(
                    classifyPreflop(hole),
                    null);
        }
        List<String> all = new ArrayList<>();
        if (hole != null) {
            all.addAll(hole);
        }
        all.addAll(community);
        HandStrength hs = evaluateBestHand(all);
        return classifyPostflop(hs, hole, community, stage);
    }

    public static DpNpcHandSnapshot classifyPostflop(
            HandStrength hs,
            List<String> hole,
            List<String> community,
            String stage) {
        if (hs == null || hole == null || community == null || hole.size() < 2) {
            return DpNpcHandSnapshot.postflop(
                    DpNpcMadeHandCategory.HIGH_CARD,
                    DpNpcDrawCategory.NONE,
                    hs,
                    false,
                    false,
                    false,
                    false,
                    false,
                    DpBoardTexture.analyze(community));
        }

        boolean holeContributes = bestFiveUsesHole(hole, community, hs);
        boolean[] playingTheBoard = {false};
        boolean[] counterfeit = {false};
        boolean[] pocketSet = {false};
        boolean[] overpair = {false};

        DpNpcMadeHandCategory made = mapMadeCategory(hs, hole, community,
                playingTheBoard,
                counterfeit,
                pocketSet,
                overpair);

        if (!holeContributes && community.size() >= 5) {
            playingTheBoard[0] = true;
            if (made.ordinal() > DpNpcMadeHandCategory.HIGH_CARD.ordinal()) {
                made = DpNpcMadeHandCategory.HIGH_CARD;
            }
        }

        DpNpcDrawCategory draw = DpDrawDetector.detect(hs, hole, community, stage);

        return DpNpcHandSnapshot.postflop(
                made,
                draw,
                hs,
                playingTheBoard[0],
                counterfeit[0],
                holeContributes,
                pocketSet[0],
                overpair[0],
                DpBoardTexture.analyze(community));
    }

    public static DpNpcPreflopCategory classifyPreflop(List<String> hole) {
        return DpNpcPreflopHandGrouper.preflopCategoryOf(hole);
    }

    private static DpNpcMadeHandCategory mapMadeCategory(
            HandStrength hs,
            List<String> hole,
            List<String> community,
            boolean[] playingTheBoardOut,
            boolean[] counterfeitOut,
            boolean[] pocketSetOut,
            boolean[] overpairOut) {
        int cat = hs.rankCategory;
        List<Integer> rk = hs.ranks;
        int boardHigh = maxRankOnBoard(community);
        int boardLow = minRankOnBoard(community);
        int boardSecond = secondHighestBoardRank(community);

        if (cat >= 9) {
            return DpNpcMadeHandCategory.ROCKET;
        }
        if (cat == 8) {
            return DpNpcMadeHandCategory.QUADS;
        }
        if (cat == 7) {
            return DpNpcMadeHandCategory.FULL_HOUSE;
        }
        if (cat == 6) {
            return DpNpcMadeHandCategory.FLUSH;
        }
        if (cat == 5) {
            return DpNpcMadeHandCategory.STRAIGHT;
        }
        if (cat == 4) {
            return classifyTrips(rk, hole, community, pocketSetOut, playingTheBoardOut, counterfeitOut);
        }
        if (cat == 3) {
            return classifyTwoPair(hole, community, rk, boardHigh,
                    playingTheBoardOut, counterfeitOut);
        }
        if (cat == 2) {
            return classifyOnePair(hs, hole, community, rk, boardHigh, boardLow, boardSecond,
                    playingTheBoardOut, overpairOut);
        }
        return DpNpcMadeHandCategory.HIGH_CARD;
    }

    private static DpNpcMadeHandCategory classifyTrips(
            List<Integer> rk,
            List<String> hole,
            List<String> community,
            boolean[] pocketSetOut,
            boolean[] playingTheBoardOut,
            boolean[] counterfeitOut) {
        if (rk == null || rk.isEmpty()) {
            return DpNpcMadeHandCategory.TRIPS;
        }
        int tripRank = rk.get(0);
        int holeMatches = countRankInHole(hole, tripRank);
        if (holeMatches == 0) {
            return DpNpcMadeHandCategory.MIDDLE_PAIR;
        }
        if (holeMatches == 2) {
            pocketSetOut[0] = true;
            return DpNpcMadeHandCategory.TRIPS;
        }
        int boardTripCount = countRankOnBoard(community, tripRank);
        if (boardTripCount >= 2) {
            int otherHole = otherHoleRankExcluding(hole, tripRank);
            if (otherHole > 0 && otherHole <= 6) {
                if (hasBroadwaySideCardOnBoard(community, tripRank)) {
                    playingTheBoardOut[0] = true;
                    return DpNpcMadeHandCategory.HIGH_CARD;
                }
                counterfeitOut[0] = true;
                return DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER;
            }
        }
        return DpNpcMadeHandCategory.TRIPS;
    }

    private static int countRankOnBoard(List<String> community, int rank) {
        if (community == null) {
            return 0;
        }
        int n = 0;
        for (String c : community) {
            if (getRankFromCard(c) == rank) {
                n++;
            }
        }
        return n;
    }

    private static boolean hasBroadwaySideCardOnBoard(List<String> community, int tripRank) {
        if (community == null) {
            return false;
        }
        for (String c : community) {
            int r = getRankFromCard(c);
            if (r >= 12 && r != tripRank) {
                return true;
            }
        }
        return false;
    }

    private static int otherHoleRankExcluding(List<String> hole, int excludeRank) {
        if (hole == null) {
            return 0;
        }
        for (String c : hole) {
            int r = getRankFromCard(c);
            if (r > 0 && r != excludeRank) {
                return r;
            }
        }
        return 0;
    }

    private static DpNpcMadeHandCategory classifyTwoPair(
            List<String> hole,
            List<String> community,
            List<Integer> rk,
            int boardHigh,
            boolean[] playingTheBoardOut,
            boolean[] counterfeitOut) {
        if (rk == null || rk.size() < 2) {
            return DpNpcMadeHandCategory.TWO_PAIR;
        }
        int highPair = rk.get(0);
        int lowPair = rk.get(1);

        if (isBoardTwoPair(community)) {
            playingTheBoardOut[0] = true;
            return DpNpcMadeHandCategory.HIGH_CARD;
        }

        if (isFakeTwoPairPairedBoard(hole, community, highPair)) {
            counterfeitOut[0] = true;
            return DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER;
        }

        boolean heroHigh = countRankInHole(hole, highPair) > 0;
        boolean heroLow = countRankInHole(hole, lowPair) > 0;
        if (!heroHigh && !heroLow) {
            playingTheBoardOut[0] = true;
            return DpNpcMadeHandCategory.HIGH_CARD;
        }

        if (!heroHigh && heroLow) {
            int boardPairRank = findBoardPairRank(community);
            if (boardPairRank > lowPair && boardPairRank == boardHigh) {
                counterfeitOut[0] = true;
                return DpNpcMadeHandCategory.MIDDLE_PAIR;
            }
        }

        return DpNpcMadeHandCategory.TWO_PAIR;
    }

    private static DpNpcMadeHandCategory classifyOnePair(
            HandStrength hs,
            List<String> hole,
            List<String> community,
            List<Integer> rk,
            int boardHigh,
            int boardLow,
            int boardSecond,
            boolean[] playingTheBoardOut,
            boolean[] overpairOut) {
        if (isPlayingBoardPairOnly(hs, hole, community)) {
            playingTheBoardOut[0] = true;
            return DpNpcMadeHandCategory.HIGH_CARD;
        }
        if (rk == null || rk.isEmpty()) {
            return DpNpcMadeHandCategory.HIGH_CARD;
        }
        int pairRank = rk.get(0);
        int heroKicker = rk.size() > 1 ? rk.get(1) : 0;

        if (isPocketPair(hole) && pairRank > boardHigh) {
            overpairOut[0] = true;
            return DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER;
        }

        if (pairRank == boardHigh) {
            if (heroKicker >= 13) {
                return DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER;
            }
            if (heroKicker == 14 && !boardContainsRank(community, 14)) {
                return DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER;
            }
            if (heroKicker > boardSecond && heroKicker >= 11) {
                return DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER;
            }
            return DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER;
        }

        if (pairRank == boardLow || (isPocketPair(hole) && pairRank < boardLow)) {
            return DpNpcMadeHandCategory.BOTTOM_PAIR;
        }
        return DpNpcMadeHandCategory.MIDDLE_PAIR;
    }

    private static boolean bestFiveUsesHole(List<String> hole, List<String> community, HandStrength hs) {
        List<String> all = new ArrayList<>(hole);
        all.addAll(community);
        List<String> best5 = getBestHandCards(all);
        if (best5 == null || best5.isEmpty()) {
            return true;
        }
        for (String h : hole) {
            if (h != null && best5.contains(h)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPocketPair(List<String> hole) {
        if (hole == null || hole.size() < 2) {
            return false;
        }
        int r1 = getRankFromCard(hole.get(0));
        int r2 = getRankFromCard(hole.get(1));
        return r1 > 0 && r1 == r2;
    }

    private static int countRankInHole(List<String> hole, int rank) {
        if (hole == null) {
            return 0;
        }
        int n = 0;
        for (String c : hole) {
            if (getRankFromCard(c) == rank) {
                n++;
            }
        }
        return n;
    }

    private static boolean boardContainsRank(List<String> community, int rank) {
        if (community == null) {
            return false;
        }
        for (String c : community) {
            if (getRankFromCard(c) == rank) {
                return true;
            }
        }
        return false;
    }

    private static int maxRankOnBoard(List<String> community) {
        int m = 0;
        if (community != null) {
            for (String c : community) {
                m = Math.max(m, getRankFromCard(c));
            }
        }
        return m;
    }

    private static int minRankOnBoard(List<String> community) {
        int m = 15;
        if (community != null) {
            for (String c : community) {
                int r = getRankFromCard(c);
                if (r > 0) {
                    m = Math.min(m, r);
                }
            }
        }
        return m == 15 ? 0 : m;
    }

    private static int secondHighestBoardRank(List<String> community) {
        Set<Integer> ranks = new HashSet<>();
        if (community != null) {
            for (String c : community) {
                int r = getRankFromCard(c);
                if (r > 0) {
                    ranks.add(r);
                }
            }
        }
        if (ranks.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = new ArrayList<>(ranks);
        Collections.sort(sorted, Collections.reverseOrder());
        return sorted.size() >= 2 ? sorted.get(1) : sorted.get(0);
    }

    private static boolean isBoardTwoPair(List<String> community) {
        if (community == null || community.size() < 4) {
            return false;
        }
        int[] counts = new int[15];
        for (String c : community) {
            int r = getRankFromCard(c);
            if (r > 0) {
                counts[r]++;
            }
        }
        int pairs = 0;
        for (int i = 2; i <= 14; i++) {
            if (counts[i] >= 2) {
                pairs++;
            }
        }
        return pairs >= 2;
    }

    private static int findBoardPairRank(List<String> community) {
        int[] counts = new int[15];
        if (community != null) {
            for (String c : community) {
                int r = getRankFromCard(c);
                if (r > 0) {
                    counts[r]++;
                }
            }
        }
        int best = 0;
        for (int i = 14; i >= 2; i--) {
            if (counts[i] >= 2) {
                best = i;
                break;
            }
        }
        return best;
    }

    /**
     * 公牌已有对子且 hero 仅一张匹配高对 → 假两对（实为顶对 + 公对）。
     */
    private static boolean isFakeTwoPairPairedBoard(List<String> hole, List<String> community, int highPair) {
        if (community == null || hole == null) {
            return false;
        }
        int onBoard = 0;
        for (String c : community) {
            if (getRankFromCard(c) == highPair) {
                onBoard++;
            }
        }
        if (onBoard < 2) {
            return false;
        }
        return countRankInHole(hole, highPair) == 1;
    }
}
