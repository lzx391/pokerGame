package com.example.mgdemoplus.npc.eval;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class NpcEvalTestSupport {

    private NpcEvalTestSupport() {
    }

    static List<String> hole(String... cardGroups) {
        return parseCardList(cardGroups);
    }

    static List<String> board(String... cardGroups) {
        return parseCardList(cardGroups);
    }

    /** 单参内用 {@code _} 分隔多张牌，如 {@code "2h_3h"}、{@code "6s_Ah_Kd_5c"} */
    static List<String> parseCardList(String... cardGroups) {
        return Arrays.stream(cardGroups)
                .flatMap(group -> Arrays.stream(group.split("_")))
                .filter(s -> !s.isBlank())
                .map(NpcEvalTestSupport::parseCompactOrFullCard)
                .collect(Collectors.toList());
    }

    /** 方案 §7 记法：{@code 2h}（rank+suit 紧凑）→ {@code hearts_2} */
    static String parseCompactOrFullCard(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("empty card token");
        }
        if (token.startsWith("hearts_") || token.startsWith("diamonds_")
                || token.startsWith("clubs_") || token.startsWith("spades_")) {
            return token;
        }
        int len = token.length();
        if (len < 2) {
            throw new IllegalArgumentException("invalid card token: " + token);
        }
        char suitChar = token.charAt(len - 1);
        String rank = token.substring(0, len - 1);
        if ("T".equalsIgnoreCase(rank)) {
            rank = "10";
        }
        return suitName(suitChar) + "_" + rank;
    }

    private static String suitName(char s) {
        return switch (Character.toLowerCase(s)) {
            case 'h' -> "hearts";
            case 'd' -> "diamonds";
            case 'c' -> "clubs";
            case 's' -> "spades";
            default -> throw new IllegalArgumentException("unknown suit: " + s);
        };
    }
}
