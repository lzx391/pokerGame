package com.example.mgdemoplus.npc.tabletalk;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code classpath:npc-lines/{style}.yaml} 解析结果。
 */
public final class NpcLinePool {

    private final boolean enabled;
    private final double speakProbability;
    private final double settleSpeakProbability;
    private final Map<String, List<String>> pools;

    public NpcLinePool(
            boolean enabled,
            double speakProbability,
            double settleSpeakProbability,
            Map<String, List<String>> pools) {
        this.enabled = enabled;
        this.speakProbability = speakProbability;
        this.settleSpeakProbability = settleSpeakProbability;
        this.pools = pools != null ? pools : Collections.emptyMap();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getSpeakProbability() {
        return speakProbability;
    }

    /** 本手进入 {@code settled} 后的发言概率（通常高于行动中 {@link #speakProbability}）。 */
    public double getSettleSpeakProbability() {
        return settleSpeakProbability;
    }

    /** 行动分桶台词；无条目或空数组时不推送（不再降级 GENERIC）。 */
    public List<String> linesForActionKey(String actionKey) {
        return linesForPoolKey(actionKey);
    }

    /** 结算分桶 {@code SETTLE_WIN} / {@code SETTLE_LOSE} 等；严格匹配，无兜底池。 */
    public List<String> linesForPoolKey(String poolKey) {
        if (poolKey == null) {
            return Collections.emptyList();
        }
        List<String> direct = pools.get(poolKey);
        if (direct == null || direct.isEmpty()) {
            return Collections.emptyList();
        }
        return direct;
    }
}
