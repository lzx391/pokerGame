package com.example.mgdemoplus.npc.rulethink;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则 NPC 思考延时（前缀 {@code dp.npc.rule-think}）。
 * 与 LLM 路径共用 {@code DpPlayer.nextBotActionTime} 字段，但仅由 {@link com.example.mgdemoplus.npc.engine.DpNpcEngine#decideActionIfReady} 读写。
 */
@ConfigurationProperties(prefix = "dp.npc.rule-think")
public class DpNpcRuleThinkProperties {

    /** 总开关；false 时采样恒为 0，行为等同即时决策。 */
    private boolean enabled = true;

    /** 秒出概率（0~1）：本次行动 delay=0，本 tick 内直接决策。 */
    private double snapProbability = 0.18;

    /** 采样结果硬上限（毫秒）。 */
    private int maxMs = 4000;

    /** 非秒出时快桶均匀采样下界（含）。 */
    private int fastMinMs = 500;

    /** 非秒出时快桶均匀采样上界（含）。 */
    private int fastMaxMs = 2000;

    /** 非秒出时选快桶的相对权重。 */
    private double fastWeight = 0.70;

    /** 非秒出时慢桶下界（含）。 */
    private int slowMinMs = 2000;

    /** 非秒出时慢桶上界（含）；仍受 {@link #maxMs} 限制。 */
    private int slowMaxMs = 4000;

    /** 非秒出时选慢桶的相对权重。 */
    private double slowWeight = 0.30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getSnapProbability() {
        return snapProbability;
    }

    public void setSnapProbability(double snapProbability) {
        this.snapProbability = snapProbability;
    }

    public int getMaxMs() {
        return maxMs;
    }

    public void setMaxMs(int maxMs) {
        this.maxMs = maxMs;
    }

    public int getFastMinMs() {
        return fastMinMs;
    }

    public void setFastMinMs(int fastMinMs) {
        this.fastMinMs = fastMinMs;
    }

    public int getFastMaxMs() {
        return fastMaxMs;
    }

    public void setFastMaxMs(int fastMaxMs) {
        this.fastMaxMs = fastMaxMs;
    }

    public double getFastWeight() {
        return fastWeight;
    }

    public void setFastWeight(double fastWeight) {
        this.fastWeight = fastWeight;
    }

    public int getSlowMinMs() {
        return slowMinMs;
    }

    public void setSlowMinMs(int slowMinMs) {
        this.slowMinMs = slowMinMs;
    }

    public int getSlowMaxMs() {
        return slowMaxMs;
    }

    public void setSlowMaxMs(int slowMaxMs) {
        this.slowMaxMs = slowMaxMs;
    }

    public double getSlowWeight() {
        return slowWeight;
    }

    public void setSlowWeight(double slowWeight) {
        this.slowWeight = slowWeight;
    }
}
