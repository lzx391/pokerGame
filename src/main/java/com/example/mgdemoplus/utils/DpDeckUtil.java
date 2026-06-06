package com.example.mgdemoplus.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 标准 52 张牌编码（{@code hearts_A}）校验与带前缀洗牌。 */
public final class DpDeckUtil {

    private static final String[] SUITS = { "hearts", "diamonds", "clubs", "spades" };
    private static final String[] RANKS = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };
    private static final Set<String> FULL_DECK = buildFullDeckSet();

    private DpDeckUtil() {
    }

    private static Set<String> buildFullDeckSet() {
        Set<String> set = new HashSet<>();
        for (String s : SUITS) {
            for (String r : RANKS) {
                set.add(s + "_" + r);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public static List<String> fullDeckInOrder() {
        List<String> deck = new ArrayList<>(52);
        for (String s : SUITS) {
            for (String r : RANKS) {
                deck.add(s + "_" + r);
            }
        }
        return deck;
    }

    public static boolean isValidCardCode(String code) {
        return code != null && FULL_DECK.contains(code);
    }

    /**
     * @return 错误信息；{@code null} 表示合法（含空列表，表示清空预设）
     */
    public static String validatePrefix(List<String> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        if (cards.size() > 52) {
            return "牌序长度不能超过52张";
        }
        Set<String> seen = new HashSet<>();
        for (String c : cards) {
            if (c == null || c.isBlank()) {
                return "牌编码不能为空";
            }
            String trimmed = c.trim();
            if (!isValidCardCode(trimmed)) {
                return "非法牌编码: " + trimmed;
            }
            if (!seen.add(trimmed)) {
                return "牌序内重复: " + trimmed;
            }
        }
        return null;
    }

    /** {@code prefix} 已校验无重复且均为合法编码；剩余牌随机补全至 52 张。 */
    public static List<String> shuffleDeckWithPrefix(List<String> prefix) {
        List<String> deck = new ArrayList<>();
        Set<String> used = new HashSet<>();
        if (prefix != null) {
            for (String c : prefix) {
                deck.add(c);
                used.add(c);
            }
        }
        List<String> rest = new ArrayList<>();
        for (String c : fullDeckInOrder()) {
            if (!used.contains(c)) {
                rest.add(c);
            }
        }
        Collections.shuffle(rest);
        deck.addAll(rest);
        return deck;
    }
}
