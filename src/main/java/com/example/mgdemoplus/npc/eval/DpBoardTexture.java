package com.example.mgdemoplus.npc.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.CARD_RANK_MAP;

/**
 * 公共牌面风险与干湿特征，供 Strategy / HandPlan / 权益修正使用。
 */
public final class DpBoardTexture {

    /** 至少一对点数在公牌出现 ≥2 次 */
    public final boolean paired;
    /** 公牌上存在两对不同点数各 ≥2 次（通常 turn+） */
    public final boolean doublePaired;
    /** flop 三张同花或 board 上某花色 ≥3 */
    public final boolean monotone;
    /** board 上某花色已达 4 张（成花面或一张成花） */
    public final boolean fourFlush;
    /** 存在 3+ 连续点数窗口（顺子面/听顺可能） */
    public final boolean straightPossible;
    /** 公牌最高牌 ≥ Q */
    public final boolean highCardBoard;
    /** 公牌最高牌 ≤ 8 */
    public final boolean lowCardBoard;
    /** 综合湿面：同花/顺子/高连接面 */
    public final boolean wet;
    /** 最大同花色张数 */
    public final int maxSuitCount;
    /** 最长连续点数 run */
    public final int maxRankRun;

    public DpBoardTexture(
            boolean paired,
            boolean doublePaired,
            boolean monotone,
            boolean fourFlush,
            boolean straightPossible,
            boolean highCardBoard,
            boolean lowCardBoard,
            boolean wet,
            int maxSuitCount,
            int maxRankRun) {
        this.paired = paired;
        this.doublePaired = doublePaired;
        this.monotone = monotone;
        this.fourFlush = fourFlush;
        this.straightPossible = straightPossible;
        this.highCardBoard = highCardBoard;
        this.lowCardBoard = lowCardBoard;
        this.wet = wet;
        this.maxSuitCount = maxSuitCount;
        this.maxRankRun = maxRankRun;
    }

    /** 顶对/超对需警惕：湿面、顺子面、四同花 */
    public boolean dangerousForTopPair() {
        return wet || straightPossible || fourFlush || monotone;
    }

    /** 两对+需警惕：四同花、双公对、强顺子面 */
    public boolean dangerousForTwoPairPlus() {
        return fourFlush || doublePaired || (straightPossible && maxRankRun >= 4);
    }

    /** 干燥面：非湿、非公对、无四同花 */
    public boolean isDry() {
        return !wet && !paired && !fourFlush;
    }

    /** LLM 局面包紧凑 token，如 {@code WET|P|MON|STR4}。 */
    public String compactToken() {
        StringBuilder sb = new StringBuilder(wet ? "WET" : "DRY");
        if (paired) {
            sb.append("|P");
        }
        if (doublePaired) {
            sb.append("|DP");
        }
        if (monotone) {
            sb.append("|MON");
        }
        if (fourFlush) {
            sb.append("|4FL");
        }
        if (straightPossible) {
            sb.append("|STR").append(maxRankRun);
        }
        if (highCardBoard) {
            sb.append("|HI");
        }
        if (lowCardBoard) {
            sb.append("|LO");
        }
        return sb.toString();
    }

    public static DpBoardTexture analyze(List<String> communityCards) {
        if (communityCards == null || communityCards.size() < 3) {
            return dryEmpty();
        }
        Map<Integer, Integer> rankCount = new HashMap<>();
        Map<String, Integer> suitCount = new HashMap<>();
        Set<Integer> ranks = new HashSet<>();
        int boardHigh = 0;
        int boardLow = 99;

        for (String c : communityCards) {
            if (c == null || !c.contains("_")) {
                continue;
            }
            String[] parts = c.split("_", 2);
            if (parts.length != 2) {
                continue;
            }
            String suit = parts[0];
            int r = CARD_RANK_MAP.getOrDefault(parts[1], 0);
            if (r > 0) {
                ranks.add(r);
                rankCount.merge(r, 1, Integer::sum);
                boardHigh = Math.max(boardHigh, r);
                boardLow = Math.min(boardLow, r);
            }
            suitCount.merge(suit, 1, Integer::sum);
        }

        int maxSuit = 0;
        for (int cnt : suitCount.values()) {
            maxSuit = Math.max(maxSuit, cnt);
        }

        int maxRun = longestRankRun(ranks);
        boolean paired = rankCount.values().stream().anyMatch(n -> n >= 2);
        int pairKinds = (int) rankCount.values().stream().filter(n -> n >= 2).count();
        boolean doublePaired = pairKinds >= 2;
        boolean monotone = maxSuit >= 3 && communityCards.size() == 3;
        boolean fourFlush = maxSuit >= 4;
        boolean straightPossible = maxRun >= 3;
        boolean highCardBoard = boardHigh >= 12;
        boolean lowCardBoard = boardHigh > 0 && boardHigh <= 8;

        boolean wet = fourFlush
                || maxSuit >= 3
                || straightPossible
                || (highCardBoard && maxRun >= 2);

        return new DpBoardTexture(
                paired,
                doublePaired,
                monotone,
                fourFlush,
                straightPossible,
                highCardBoard,
                lowCardBoard,
                wet,
                maxSuit,
                maxRun);
    }

    private static int longestRankRun(Set<Integer> ranks) {
        if (ranks == null || ranks.size() < 2) {
            return ranks == null ? 0 : ranks.size();
        }
        List<Integer> list = new ArrayList<>(ranks);
        Collections.sort(list);
        int maxRun = 1;
        int current = 1;
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) == list.get(i - 1) + 1) {
                current++;
                maxRun = Math.max(maxRun, current);
            } else if (list.get(i) > list.get(i - 1) + 1) {
                current = 1;
            }
        }
        // wheel: A-2-3-4-5
        if (ranks.contains(14) && ranks.contains(2) && ranks.contains(3)) {
            int wheel = 1;
            if (ranks.contains(4)) {
                wheel++;
            }
            if (ranks.contains(5)) {
                wheel++;
            }
            maxRun = Math.max(maxRun, wheel);
        }
        return maxRun;
    }

    private static DpBoardTexture dryEmpty() {
        return new DpBoardTexture(false, false, false, false, false, false, false, false, 0, 0);
    }
}
