package com.example.mgdemoplus.moderation;

/**
 * 敏感词匹配前的文本规范化（去常见干扰符、拉丁字母小写）。
 */
public final class DpTextNormalize {

    private DpTextNormalize() {
    }

    /**
     * 用于「是否包含敏感词」判断：去掉空白与常见分隔符，拉丁字母转小写。
     */
    public static String compactForMatch(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '_' || c == '-' || c == '.' || c == '*' || c == '·' || c == '•') {
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + ('a' - 'A')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
