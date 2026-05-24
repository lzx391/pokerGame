package com.example.mgdemoplus.npc.rulethink;

import java.util.Random;

/**
 * 规则 NPC 思考延时采样；供静态 {@link com.example.mgdemoplus.npc.engine.DpNpcEngine} 调用。
 */
public final class DpNpcRuleThinkSampler {

    private static volatile DpNpcRuleThinkProperties properties = new DpNpcRuleThinkProperties();

    private DpNpcRuleThinkSampler() {
    }

    public static void bind(DpNpcRuleThinkProperties props) {
        properties = props != null ? props : new DpNpcRuleThinkProperties();
    }

    public static DpNpcRuleThinkProperties currentProperties() {
        return properties;
    }

    /** 采样本次行动的思考延时（毫秒）；{@code enabled=false} 或秒出时返回 0。 */
    public static long sampleDelayMs(Random rng) {
        return sampleDelayMs(properties, rng);
    }

    public static long sampleDelayMs(DpNpcRuleThinkProperties config, Random rng) {
        if (config == null || !config.isEnabled()) {
            return 0L;
        }
        double snap = clampProbability(config.getSnapProbability());
        if (rng.nextDouble() < snap) {
            return 0L;
        }

        double fastW = config.getFastWeight() > 0 ? config.getFastWeight() : 0.70;
        double slowW = config.getSlowWeight() > 0 ? config.getSlowWeight() : 0.30;
        double total = fastW + slowW;
        if (total <= 0) {
            total = 1.0;
            fastW = 0.70;
            slowW = 0.30;
        }
        boolean pickFast = rng.nextDouble() < (fastW / total);

        int lo;
        int hi;
        if (pickFast) {
            lo = config.getFastMinMs();
            hi = config.getFastMaxMs();
        } else {
            lo = config.getSlowMinMs();
            hi = config.getSlowMaxMs();
        }
        if (lo > hi) {
            int tmp = lo;
            lo = hi;
            hi = tmp;
        }

        int delay = lo;
        if (hi > lo) {
            delay = lo + rng.nextInt(hi - lo + 1);
        }
        int cap = Math.max(0, config.getMaxMs());
        return Math.min(delay, cap);
    }

    private static double clampProbability(double p) {
        if (p < 0) {
            return 0;
        }
        if (p > 1) {
            return 1;
        }
        return p;
    }
}
