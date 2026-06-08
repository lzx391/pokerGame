package com.example.mgdemoplus.npc.eval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻前起手牌 G1–G8 分组与 {@link DpNpcPreflopCategory} 映射。
 * 从 {@code strategypro/preflop/DpNpcUnifiedPreflopStrategy} 抽出，供 eval 与翻前策略共用，消除循环依赖。
 */
public final class DpNpcPreflopHandGrouper {

    private DpNpcPreflopHandGrouper() {
    }

    private static final Map<String, Integer> RANK = new HashMap<>();

    static {
        RANK.put("2", 2);
        RANK.put("3", 3);
        RANK.put("4", 4);
        RANK.put("5", 5);
        RANK.put("6", 6);
        RANK.put("7", 7);
        RANK.put("8", 8);
        RANK.put("9", 9);
        RANK.put("10", 10);
        RANK.put("J", 11);
        RANK.put("Q", 12);
        RANK.put("K", 13);
        RANK.put("A", 14);
    }

    public enum HandGroup {
        G1,
        G2,
        G3,
        G4,
        G5,
        G6,
        G7,
        G8
    }

    public static final class HoleInfo {
        public final boolean valid;
        public final int r1;
        public final int r2;
        public final boolean suited;

        public HoleInfo(boolean valid, int r1, int r2, boolean suited) {
            this.valid = valid;
            this.r1 = r1;
            this.r2 = r2;
            this.suited = suited;
        }
    }

    public static HoleInfo parseHole(List<String> holeCards) {
        if (holeCards == null || holeCards.size() < 2) {
            return new HoleInfo(false, 0, 0, false);
        }
        Card a = parseCard(holeCards.get(0));
        Card b = parseCard(holeCards.get(1));
        if (!a.valid || !b.valid) {
            return new HoleInfo(false, 0, 0, false);
        }
        return new HoleInfo(true, a.rank, b.rank, a.suit.equals(b.suit));
    }

    public static HandGroup groupOf(HoleInfo h) {
        int a = Math.max(h.r1, h.r2);
        int b = Math.min(h.r1, h.r2);
        boolean pair = a == b;
        if (pair) {
            if (a >= 11) {
                return HandGroup.G1;
            }
            if (a == 10) {
                return HandGroup.G2;
            }
            if (a == 9 || a == 8) {
                return HandGroup.G3;
            }
            if (a == 7 || a == 6) {
                return HandGroup.G4;
            }
            return HandGroup.G5;
        }

        boolean suited = h.suited;
        boolean hasAce = (a == 14);
        boolean broadwayA = (a >= 10);
        boolean broadwayB = (b >= 10);
        int gap = a - b;

        if (hasAce && b >= 11) {
            if (a == 14 && b == 13) {
                return HandGroup.G1;
            }
            if (b == 12) {
                return HandGroup.G2;
            }
            return suited ? HandGroup.G2 : HandGroup.G3;
        }

        if (hasAce && suited && b >= 5 && b <= 10) {
            if (b >= 9) {
                return HandGroup.G4;
            }
            return HandGroup.G5;
        }

        if (suited && broadwayA && broadwayB) {
            if (a >= 13 && b >= 10) {
                return HandGroup.G2;
            }
            return HandGroup.G3;
        }

        if (!suited && broadwayA && broadwayB) {
            if (a >= 13 && b >= 11) {
                return HandGroup.G3;
            }
            if (a >= 12 && b >= 11) {
                return HandGroup.G4;
            }
            return HandGroup.G6;
        }

        if (suited) {
            if (gap == 1 && a >= 6) {
                return HandGroup.G5;
            }
            if (gap == 2 && a >= 9) {
                return HandGroup.G6;
            }
        }

        if (suited && a >= 11) {
            return HandGroup.G6;
        }
        if (suited && a >= 9) {
            return HandGroup.G7;
        }

        return HandGroup.G8;
    }

    public static DpNpcPreflopCategory preflopCategoryOf(List<String> holeCards) {
        HandGroup g = groupOf(parseHole(holeCards));
        return switch (g) {
            case G1 -> DpNpcPreflopCategory.PREMIUM;
            case G2 -> DpNpcPreflopCategory.STRONG;
            case G3, G4 -> DpNpcPreflopCategory.PLAYABLE;
            case G5 -> DpNpcPreflopCategory.SPECULATIVE;
            case G6, G7 -> DpNpcPreflopCategory.MARGINAL;
            case G8 -> DpNpcPreflopCategory.TRASH;
        };
    }

    private static final class Card {
        final boolean valid;
        final String suit;
        final int rank;

        Card(boolean valid, String suit, int rank) {
            this.valid = valid;
            this.suit = suit;
            this.rank = rank;
        }
    }

    private static Card parseCard(String c) {
        if (c == null || !c.contains("_")) {
            return new Card(false, "", 0);
        }
        String[] parts = c.split("_", 2);
        if (parts.length != 2) {
            return new Card(false, "", 0);
        }
        String suit = parts[0];
        String rankStr = parts[1];
        int r = RANK.getOrDefault(rankStr, 0);
        if (r <= 0) {
            return new Card(false, "", 0);
        }
        return new Card(true, suit, r);
    }
}
