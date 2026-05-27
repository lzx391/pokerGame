package com.example.mgdemoplus.history.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 牌谱详情 payload 按观看者脱敏（读出后、响应前；不改库内 payload_json）。
 * 规则与前端 {@code HandHistoryDetail.vue} / {@code dpHandHistoryReplay.js} 一致：存在 FOLD 的玩家对他人隐藏洞牌与牌型。
 */
public final class DpHandHistoryPayloadSanitizer {

    private static final ObjectMapper CLONE_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private DpHandHistoryPayloadSanitizer() {
    }

    /**
     * 给它一个昵称和历史对局，能返回裁剪后的信息
     * @param payload         反序列化后的 payload（不会被原地修改）
     * @param viewerNickname  当前登录用户昵称；为 null 时无人视为「本人」
     * @return 脱敏后的深拷贝；payload 为 null 时返回 null
     */
    public static Map<String, Object> sanitize(Map<String, Object> payload, String viewerNickname) {
        if (payload == null) {
            return null;
        }
        if (payload.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> out = CLONE_MAPPER.convertValue(payload, MAP_TYPE);
        Set<String> foldedNicknames = collectFoldedNicknames(out);
        if (foldedNicknames.isEmpty()) {
            return out;
        }

        for (String nick : foldedNicknames) {
            if (nick == null || nick.isEmpty()) {
                continue;
            }
            if (nick.equals(viewerNickname)) {
                continue;
            }
            removeFromHoleCardsAtEnd(out, nick);
            removeFromBoardHandRanks(out, nick);
        }
        return out;
    }

    static Set<String> collectFoldedNicknames(Map<String, Object> payload) {
        Object actionsObj = payload.get("actions");
        if (!(actionsObj instanceof List<?> actions)) {
            return Set.of();
        }
        Set<String> folded = new HashSet<>();
        for (Object item : actions) {
            if (!(item instanceof Map<?, ?> action)) {
                continue;
            }
            if (!"FOLD".equals(action.get("type"))) {
                continue;
            }
            Object actor = action.get("actorNickname");
            if (actor instanceof String s && !s.isEmpty()) {
                folded.add(s);
            }
        }
        return folded;
    }

    @SuppressWarnings("unchecked")
    private static void removeFromHoleCardsAtEnd(Map<String, Object> payload, String nickname) {
        Object holesObj = payload.get("holeCardsAtEnd");
        if (!(holesObj instanceof Map<?, ?> holes)) {
            return;
        }
        Map<String, Object> mutable = new LinkedHashMap<>((Map<String, Object>) holes);
        mutable.remove(nickname);
        payload.put("holeCardsAtEnd", mutable);
    }

    @SuppressWarnings("unchecked")
    private static void removeFromBoardHandRanks(Map<String, Object> payload, String nickname) {
        Object boardsObj = payload.get("boardsByStreet");
        if (!(boardsObj instanceof List<?> boards)) {
            return;
        }
        for (int i = 0; i < boards.size(); i++) {
            Object boardObj = boards.get(i);
            if (!(boardObj instanceof Map<?, ?> board)) {
                continue;
            }
            Object ranksObj = board.get("handRankNameByPlayer");
            if (!(ranksObj instanceof Map<?, ?> ranks) || !ranks.containsKey(nickname)) {
                continue;
            }
            Map<String, Object> boardCopy = new LinkedHashMap<>((Map<String, Object>) board);
            Map<String, Object> ranksCopy = new LinkedHashMap<>((Map<String, Object>) ranks);
            ranksCopy.remove(nickname);
            boardCopy.put("handRankNameByPlayer", ranksCopy);
            ((List<Object>) boards).set(i, boardCopy);
        }
    }
}
