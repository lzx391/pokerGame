package com.example.mgdemoplus.npc.mood;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则 NPC 情绪（mood）配置：仅影响结算后的 mood 数值与桌边话术分桶，不参与决策。
 * 前缀 {@code dp.npc.mood}。
 */
@ConfigurationProperties(prefix = "dp.npc.mood")
public class DpNpcMoodProperties {

    /** 总开关；false 时结算不改写 mood，台词与现网一致（不按 mood 分桶）。 */
    private boolean enabled = true;

    /** 本手净赢筹码 &gt; 0 时的 mood 增量。 */
    private double deltaWin = 0.2;

    /** 本手净输且筹码低于进房初始时的 mood 增量（通常为负）。 */
    private double deltaLose = -0.2;

    /** mood ≥ 此阈值 → 台词 {@link NpcMoodState#HIGH} 桶（仅台词，不影响决策）。 */
    private double highThreshold = 0.35;

    /** mood ≤ 此阈值 → 台词 {@link NpcMoodState#LOW} 桶。 */
    private double lowThreshold = -0.35;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getDeltaWin() {
        return deltaWin;
    }

    public void setDeltaWin(double deltaWin) {
        this.deltaWin = deltaWin;
    }

    public double getDeltaLose() {
        return deltaLose;
    }

    public void setDeltaLose(double deltaLose) {
        this.deltaLose = deltaLose;
    }

    public double getHighThreshold() {
        return highThreshold;
    }

    public void setHighThreshold(double highThreshold) {
        this.highThreshold = highThreshold;
    }

    public double getLowThreshold() {
        return lowThreshold;
    }

    public void setLowThreshold(double lowThreshold) {
        this.lowThreshold = lowThreshold;
    }
}
