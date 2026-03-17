package com.example.mgdemoplus.service.studentImpl;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 扑克牌型评估与辅助工具。
 * 提供：
 * - 7 选 5 的最佳牌型计算
 * - 牌型强度比较 HandStrength
 * - 粗粒度牌力枚举 SimpleStrength
 */
public final class DpHandEvaluator {

    private DpHandEvaluator() {
    }

    // 统一的点数映射
    public static final Map<String, Integer> CARD_RANK_MAP = buildCardRankMap();

    private static Map<String, Integer> buildCardRankMap() {
        Map<String, Integer> m = new HashMap<>();
        m.put("2", 2);
        m.put("3", 3);
        m.put("4", 4);
        m.put("5", 5);
        m.put("6", 6);
        m.put("7", 7);
        m.put("8", 8);
        m.put("9", 9);
        m.put("10", 10);
        m.put("J", 11);
        m.put("Q", 12);
        m.put("K", 13);
        m.put("A", 14);
        return Collections.unmodifiableMap(m);
    }

    /**
     * 手牌强度：先比牌型，再比 5 张牌从大到小的点数。
     */
    public static class HandStrength implements Comparable<HandStrength> {
        public final int rankCategory;   // 10 皇家同花顺, 9 同花顺, ..., 1 高牌
        public final List<Integer> ranks; // 从高到低的点数列表，用于打平

        public HandStrength(int rankCategory, List<Integer> ranks) {
            this.rankCategory = rankCategory;
            this.ranks = ranks;
        }

        @Override
        public int compareTo(HandStrength o) {
            if (this.rankCategory != o.rankCategory) {
                return Integer.compare(this.rankCategory, o.rankCategory);
            }
            int size = Math.min(this.ranks.size(), o.ranks.size());
            for (int i = 0; i < size; i++) {
                int c = Integer.compare(this.ranks.get(i), o.ranks.get(i));
                if (c != 0) return c;
            }
            return 0;
        }
    }

    /**
     * 对机器人和 UI 足够用的粗粒度牌力。
     */
    public enum SimpleStrength {
        WEAK,
        MEDIUM,
        STRONG,
        MONSTER
    }

    /**
     * 从 7 张以内的牌里选出构成最大牌型的 5 张，返回原始字符串列表。
     */
    public static List<String> getBestHandCards(List<String> cards) {
        List<int[]> parsed = new ArrayList<>();

        for (String c : cards) {
            if (c == null) continue;
            String[] parts = c.split("_");
            if (parts.length != 2) continue;
            String suit = parts[0];
            String rankStr = parts[1];
            Integer rank = CARD_RANK_MAP.get(rankStr);
            if (rank == null) continue;
            int suitCode;
            switch (suit) {
                case "hearts":
                    suitCode = 0;
                    break;
                case "diamonds":
                    suitCode = 1;
                    break;
                case "clubs":
                    suitCode = 2;
                    break;
                case "spades":
                    suitCode = 3;
                    break;
                default:
                    suitCode = 4;
            }
            parsed.add(new int[]{rank, suitCode});
        }

        if (parsed.size() < 5) {
            return Collections.emptyList();
        }

        int n = parsed.size();
        HandStrength best = null;
        List<String> bestCards = null;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    for (int a = k + 1; a < n; a++) {
                        for (int b = a + 1; b < n; b++) {
                            List<int[]> hand = Arrays.asList(
                                    parsed.get(i), parsed.get(j), parsed.get(k), parsed.get(a), parsed.get(b)
                            );
                            HandStrength hs = evaluateFiveCardHand(hand);
                            if (best == null || hs.compareTo(best) > 0) {
                                best = hs;
                                bestCards = Arrays.asList(
                                        cards.get(i), cards.get(j), cards.get(k), cards.get(a), cards.get(b)
                                );
                            }
                        }
                    }
                }
            }
        }

        if (bestCards == null || best == null) {
            return Collections.emptyList();
        }
        return sortCardsForDisplay(new ArrayList<>(bestCards), best);
    }

    /**
     * 从若干张牌中评估最佳 5 张牌的 HandStrength。
     */
    public static HandStrength evaluateBestHand(List<String> cards) {
        List<int[]> parsed = new ArrayList<>();

        for (String c : cards) {
            if (c == null) continue;
            String[] parts = c.split("_");
            if (parts.length != 2) continue;
            String suit = parts[0];
            String rankStr = parts[1];
            Integer rank = CARD_RANK_MAP.get(rankStr);
            if (rank == null) continue;
            int suitCode;
            switch (suit) {
                case "hearts":
                    suitCode = 0;
                    break;
                case "diamonds":
                    suitCode = 1;
                    break;
                case "clubs":
                    suitCode = 2;
                    break;
                case "spades":
                    suitCode = 3;
                    break;
                default:
                    suitCode = 4;
            }
            parsed.add(new int[]{rank, suitCode});
        }

        if (parsed.size() < 5) {
            return new HandStrength(1, Collections.singletonList(0));
        }

        int n = parsed.size();
        HandStrength best = null;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    for (int a = k + 1; a < n; a++) {
                        for (int b = a + 1; b < n; b++) {
                            List<int[]> hand = new ArrayList<>();
                            hand.add(parsed.get(i));
                            hand.add(parsed.get(j));
                            hand.add(parsed.get(k));
                            hand.add(parsed.get(a));
                            hand.add(parsed.get(b));
                            HandStrength hs = evaluateFiveCardHand(hand);
                            if (best == null || hs.compareTo(best) > 0) {
                                best = hs;
                            }
                        }
                    }
                }
            }
        }

        return best == null ? new HandStrength(1, Collections.singletonList(0)) : best;
    }

    /**
     * 五张牌的具体牌型评估。
     */
    public static HandStrength evaluateFiveCardHand(List<int[]> hand) {
        hand.sort((a, b) -> Integer.compare(b[0], a[0]));
        int[] ranks = new int[5];
        int[] suits = new int[5];
        for (int i = 0; i < 5; i++) {
            ranks[i] = hand.get(i)[0];
            suits[i] = hand.get(i)[1];
        }

        boolean isFlush = true;
        for (int i = 1; i < 5; i++) {
            if (suits[i] != suits[0]) {
                isFlush = false;
                break;
            }
        }

        boolean isStraight = false;
        int[] distinct = Arrays.stream(ranks).distinct().toArray();
        if (distinct.length == 5 && ranks[0] - ranks[4] == 4) {
            isStraight = true;
        }
        // A-5 小顺子
        if (ranks[0] == 14 && ranks[1] == 5 && ranks[2] == 4 && ranks[3] == 3 && ranks[4] == 2) {
            isStraight = true;
            ranks = new int[]{5, 4, 3, 2, 1};
        }

        Map<Integer, Integer> countMap = new HashMap<>();
        for (int r : ranks) {
            countMap.put(r, countMap.getOrDefault(r, 0) + 1);
        }

        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(countMap.entrySet());
        entries.sort((e1, e2) -> {
            int c = Integer.compare(e2.getValue(), e1.getValue());
            if (c != 0) return c;
            return Integer.compare(e2.getKey(), e1.getKey());
        });

        int firstCount = entries.get(0).getValue();
        int firstRank = entries.get(0).getKey();
        int secondCount = entries.size() > 1 ? entries.get(1).getValue() : 0;
        int secondRank = entries.size() > 1 ? entries.get(1).getKey() : 0;

        // 皇家同花顺 / 同花顺
        if (isFlush && isStraight) {
            if (ranks[0] == 14 && ranks[1] == 13) {
                return new HandStrength(10, Arrays.asList(14, 13, 12, 11, 10));
            }
            return new HandStrength(9, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
        }

        // 四条
        if (firstCount == 4) {
            List<Integer> kickers = new ArrayList<>();
            kickers.add(firstRank);
            for (int r : ranks) {
                if (r != firstRank) kickers.add(r);
            }
            return new HandStrength(8, kickers);
        }

        // 葫芦
        if (firstCount == 3 && secondCount == 2) {
            return new HandStrength(7, Arrays.asList(firstRank, secondRank));
        }

        // 同花
        if (isFlush) {
            return new HandStrength(6, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
        }

        // 顺子
        if (isStraight) {
            return new HandStrength(5, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
        }

        // 三条
        if (firstCount == 3) {
            List<Integer> kickers = new ArrayList<>();
            kickers.add(firstRank);
            for (int r : ranks) {
                if (r != firstRank) kickers.add(r);
            }
            return new HandStrength(4, kickers);
        }

        // 两对
        if (firstCount == 2 && secondCount == 2) {
            int kicker = 0;
            for (int r : ranks) {
                if (r != firstRank && r != secondRank) {
                    kicker = r;
                    break;
                }
            }
            List<Integer> list = new ArrayList<>();
            list.add(Math.max(firstRank, secondRank));
            list.add(Math.min(firstRank, secondRank));
            list.add(kicker);
            return new HandStrength(3, list);
        }

        // 一对
        if (firstCount == 2) {
            List<Integer> kickers = new ArrayList<>();
            kickers.add(firstRank);
            for (int r : ranks) {
                if (r != firstRank) kickers.add(r);
            }
            return new HandStrength(2, kickers);
        }

        // 高牌
        return new HandStrength(1, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
    }

    /**
     * 从牌字符串 "suit_rank" 解析出点数（2~14，A=14）。
     */
    public static int getRankFromCard(String card) {
        if (card == null || !card.contains("_")) return 0;
        String rankStr = card.split("_", 2)[1];
        return CARD_RANK_MAP.getOrDefault(rankStr, 0);
    }

    /**
     * 根据 HandStrength 对 5 张牌进行更「易读」的排序。
     */
    public static List<String> sortCardsForDisplay(List<String> cards, HandStrength strength) {
        if (cards == null || cards.size() != 5 || strength == null) return cards;
        int cat = strength.rankCategory;
        List<Integer> ranks = strength.ranks;

        // 高牌、顺子、同花、同花顺、皇家：按点数从大到小
        if (cat == 1 || cat == 5 || cat == 6 || cat == 9 || cat == 10) {
            boolean wheel = (cat == 5 || cat == 9) && ranks.size() >= 5 && ranks.get(0) == 5;
            cards.sort((a, b) -> {
                int ra = getRankFromCard(a);
                int rb = getRankFromCard(b);
                if (wheel) {
                    int ia = (ra == 14) ? ranks.indexOf(1) : ranks.indexOf(ra);
                    int ib = (rb == 14) ? ranks.indexOf(1) : ranks.indexOf(rb);
                    return Integer.compare(ia, ib);
                }
                return Integer.compare(rb, ra);
            });
            return cards;
        }

        // 一对、两对、三条、葫芦、四条：按 ranks 中的组顺序排
        cards.sort((a, b) -> {
            int ra = getRankFromCard(a);
            int rb = getRankFromCard(b);
            int ia = ranks.indexOf(ra);
            int ib = ranks.indexOf(rb);
            if (ia < 0) ia = 999;
            if (ib < 0) ib = 999;
            int c = Integer.compare(ia, ib);
            if (c != 0) return c;
            return Integer.compare(rb, ra);
        });
        return cards;
    }

    /**
     * 将详细 HandStrength 映射到简单档位。
     */
    public static SimpleStrength toSimpleStrength(HandStrength hs, String stage,
                                                  List<String> holeCards,
                                                  List<String> community) {
        if (hs == null) {
            // preflop 简化判断
            if (holeCards == null || holeCards.size() < 2) {
                return SimpleStrength.WEAK;
            }
            int r1 = getRankFromCard(holeCards.get(0));
            int r2 = getRankFromCard(holeCards.get(1));
            boolean pair = (r1 == r2);
            int high = Math.max(r1, r2);
            int low = Math.min(r1, r2);

            if (pair && high >= 10) {
                return SimpleStrength.STRONG;
            }
            if (pair && high >= 7) {
                return SimpleStrength.MEDIUM;
            }
            if (high >= 11 && low >= 9) {
                return SimpleStrength.MEDIUM;
            }
            return SimpleStrength.WEAK;
        }

        int cat = hs.rankCategory;
        if ("flop".equals(stage) || "turn".equals(stage) || "river".equals(stage)) {
            if (cat >= 7) {
                return SimpleStrength.MONSTER;
            }
            if (cat == 6 || cat == 5) {
                return SimpleStrength.STRONG;
            }
            if (cat == 4 || cat == 3) {
                return SimpleStrength.MEDIUM;
            }
            return SimpleStrength.WEAK;
        }

        // 其它阶段直接按档位粗分
        if (cat >= 7) {
            return SimpleStrength.MONSTER;
        }
        if (cat == 6 || cat == 5) {
            return SimpleStrength.STRONG;
        }
        if (cat == 4 || cat == 3) {
            return SimpleStrength.MEDIUM;
        }
        return SimpleStrength.WEAK;
    }
}

