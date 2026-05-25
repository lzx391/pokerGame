package com.example.mgdemoplus.npc.mood;

/**
 * 规则 NPC 台词分桶用情绪档位；{@code dp.npc.mood.enabled=false} 时桌边话固定用 NEUTRAL（calm）块。
 */
public enum NpcMoodState {
    HIGH,
    NEUTRAL,
    LOW;

    public static NpcMoodState fromMood(double mood, double highThreshold, double lowThreshold) {
        if (mood >= highThreshold) {
            return HIGH;
        }
        if (mood <= lowThreshold) {
            return LOW;
        }
        return NEUTRAL;
    }

    /** 台词池 key 前缀段，例如 {@code MOOD_HIGH_FOLD}。 */
    public String moodPoolKey(String basePoolKey) {
        return "MOOD_" + name() + "_" + basePoolKey;
    }

    /** 将 mood 限制在 [-1, 1]。 */
    public static double clampMood(double mood) {
        if (mood > 1.0) {
            return 1.0;
        }
        if (mood < -1.0) {
            return -1.0;
        }
        return mood;
    }
}
