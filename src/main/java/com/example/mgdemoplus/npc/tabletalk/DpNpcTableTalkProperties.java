package com.example.mgdemoplus.npc.tabletalk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NPC 桌边话术全局开关与默认抽样参数（前缀 {@code dp.npc.table-talk}）。
 */
@ConfigurationProperties(prefix = "dp.npc.table-talk")
public class DpNpcTableTalkProperties {

    /** 总开关；false 时规则与 LLM 路径均不推送。 */
    private boolean enabled = true;

    /**
     * 规则 NPC 说话概率（0~1）；非 null 时覆盖各 {@code npc-lines/{style}.yaml} 内默认值。
     */
    private Double speakProbability;

    /** 同一房间内同一 bot 两次推送的最小间隔（毫秒）。 */
    private long minIntervalMs = 8_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Double getSpeakProbability() {
        return speakProbability;
    }

    public void setSpeakProbability(Double speakProbability) {
        this.speakProbability = speakProbability;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }

    public void setMinIntervalMs(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }
}
