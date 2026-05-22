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
    private final Map<String, List<String>> pools;

    public NpcLinePool(boolean enabled, double speakProbability, Map<String, List<String>> pools) {
        this.enabled = enabled;
        this.speakProbability = speakProbability;
        this.pools = pools != null ? pools : Collections.emptyMap();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getSpeakProbability() {
        return speakProbability;
    }

    public List<String> linesForActionKey(String actionKey) {
        if (actionKey == null) {
            return Collections.emptyList();
        }
        List<String> direct = pools.get(actionKey);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        List<String> generic = pools.get("GENERIC");
        return generic != null ? generic : Collections.emptyList();
    }
}
