package com.example.mgdemoplus.npc.eval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则 NPC 细化牌力开关（前缀 {@code dp.npc.granular-strength}）。
 */
@ConfigurationProperties(prefix = "dp.npc.granular-strength")
public class DpNpcGranularStrengthProperties {

    /** true：12 档 + 听牌；false：回退旧 SimpleStrength 四档路径 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
