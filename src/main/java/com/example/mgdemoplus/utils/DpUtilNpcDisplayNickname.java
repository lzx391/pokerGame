package com.example.mgdemoplus.utils;

/**
 * 规则 / LLM 机器人昵称在 UI 上的短显：
 * {@code BOT_*_&lt;长 uuid&gt;} → 去 {@code -} 后取前 4 字符；{@code BOT_*_&lt;短序号&gt;} 原样展示。
 * 须与前端 {@code front/dp_game/src/utils/dpDisplayNickname.js} 保持一致。
 */
public final class DpUtilNpcDisplayNickname {
    private static final String[] HEADS = {
            "BOT_MANIAC_",
            "BOT_CALL_",
            "BOT_LLM_",
            "BOT_LAG_",
            "BOT_TAG_",
            "BOT_NIT_",
            "BOT_FISH_",
    };

    private DpUtilNpcDisplayNickname() {
    }

    public static String shortenForUi(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return nickname;
        }
        for (String head : HEADS) {
            if (!nickname.startsWith(head) || nickname.length() <= head.length()) {
                continue;
            }
            String uuidPart = nickname.substring(head.length()).replace("-", "");
            if (uuidPart.length() >= 4) {
                return head + uuidPart.substring(0, 4);
            }
            return nickname;
        }
        return nickname;
    }
}
